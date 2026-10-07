package ba.tfb.tasknest.service;

import ba.tfb.tasknest.dto.task.TaskPhotoResponse;
import ba.tfb.tasknest.entity.Task;
import ba.tfb.tasknest.entity.TaskPhoto;
import ba.tfb.tasknest.entity.enums.TaskStatus;
import ba.tfb.tasknest.exception.BusinessRuleException;
import ba.tfb.tasknest.exception.NotResourceOwnerException;
import ba.tfb.tasknest.exception.ResourceNotFoundException;
import ba.tfb.tasknest.photo.PhotoProcessor;
import ba.tfb.tasknest.photo.PhotoStorage;
import ba.tfb.tasknest.photo.ProcessedPhoto;
import ba.tfb.tasknest.repository.TaskPhotoRepository;
import ba.tfb.tasknest.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskPhotoService {

    public static final int MAX_PHOTOS_PER_TASK = 5;

    private static final Set<TaskStatus> EDITABLE = EnumSet.of(TaskStatus.DRAFT, TaskStatus.PUBLISHED);

    private final TaskRepository taskRepository;
    private final TaskPhotoRepository photoRepository;
    private final PhotoProcessor processor;
    private final PhotoStorage storage;

    @Transactional
    public TaskPhotoResponse addPhoto(UUID taskId, UUID clientId, byte[] upload) {
        Task task = loadEditableTask(taskId, clientId);
        long existing = photoRepository.countByTaskId(taskId);
        if (existing >= MAX_PHOTOS_PER_TASK) {
            throw new BusinessRuleException("A task can have at most " + MAX_PHOTOS_PER_TASK + " photos");
        }

        ProcessedPhoto processed = processor.process(upload);

        TaskPhoto photo = new TaskPhoto();
        photo.setTask(task);
        photo.setContentType(processed.format().contentType());
        photo.setSizeBytes(processed.bytes().length);
        photo.setWidth(processed.width());
        photo.setHeight(processed.height());
        photo.setPosition((int) existing);
        photo.setStorageKey(UUID.randomUUID() + "." + processed.format().extension());
        TaskPhoto saved = photoRepository.save(photo);

        storage.store(saved.getStorageKey(), processed.bytes());
        deleteFileIfRolledBack(saved.getStorageKey());

        return TaskPhotoResponse.from(saved);
    }

    @Transactional
    public void deletePhoto(UUID taskId, UUID photoId, UUID clientId) {
        loadEditableTask(taskId, clientId);
        TaskPhoto photo = photoRepository.findById(photoId)
                .filter(found -> found.getTask().getId().equals(taskId))
                .orElseThrow(() -> new ResourceNotFoundException("TaskPhoto", photoId));

        photoRepository.delete(photo);
        photoRepository.flush();

        List<TaskPhoto> remaining = photoRepository.findByTaskIdOrderByPositionAsc(taskId);
        for (int position = 0; position < remaining.size(); position++) {
            remaining.get(position).setPosition(position);
        }
        deleteFileAfterCommit(photo.getStorageKey());
    }

    @Transactional(readOnly = true)
    public StoredPhoto loadPhoto(UUID photoId) {
        TaskPhoto photo = photoRepository.findWithTaskById(photoId)
                .filter(found -> found.getTask().getStatus() != TaskStatus.REMOVED)
                .orElseThrow(() -> new ResourceNotFoundException("TaskPhoto", photoId));

        byte[] bytes = storage.load(photo.getStorageKey())
                .orElseThrow(() -> new ResourceNotFoundException("TaskPhoto", photoId));
        return new StoredPhoto(bytes, photo.getContentType());
    }

    private Task loadEditableTask(UUID taskId, UUID clientId) {
        Task task = taskRepository.findWithWriteLockById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        if (!task.getClient().getId().equals(clientId)) {
            throw new NotResourceOwnerException("Task does not belong to this user");
        }
        if (!EDITABLE.contains(task.getStatus())) {
            throw new BusinessRuleException("Photos can only be changed while the task is a draft or open for offers");
        }
        return task;
    }

    private void deleteFileIfRolledBack(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    storage.delete(key);
                }
            }
        });
    }

    private void deleteFileAfterCommit(String key) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.delete(key);
            }
        });
    }

    public record StoredPhoto(byte[] bytes, String contentType) {
    }
}
