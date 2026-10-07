package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.dto.task.TaskPhotoResponse;
import ba.tfb.tasknest.exception.BusinessRuleException;
import ba.tfb.tasknest.security.UserPrincipal;
import ba.tfb.tasknest.service.TaskPhotoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TaskPhotoController {

    private final TaskPhotoService taskPhotoService;

    @PostMapping(path = "/tasks/{taskId}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public TaskPhotoResponse upload(@PathVariable UUID taskId,
                                    @RequestParam("file") MultipartFile file,
                                    @AuthenticationPrincipal UserPrincipal principal) throws IOException {
        if (file.isEmpty()) {
            throw new BusinessRuleException("The photo is empty");
        }
        return taskPhotoService.addPhoto(taskId, principal.getId(), file.getBytes());
    }

    @DeleteMapping("/tasks/{taskId}/photos/{photoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID taskId,
                       @PathVariable UUID photoId,
                       @AuthenticationPrincipal UserPrincipal principal) {
        taskPhotoService.deletePhoto(taskId, photoId, principal.getId());
    }

    @GetMapping("/photos/{photoId}")
    public ResponseEntity<byte[]> photo(@PathVariable UUID photoId) {
        TaskPhotoService.StoredPhoto photo = taskPhotoService.loadPhoto(photoId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(photo.bytes());
    }
}
