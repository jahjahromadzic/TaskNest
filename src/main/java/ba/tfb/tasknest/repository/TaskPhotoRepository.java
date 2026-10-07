package ba.tfb.tasknest.repository;

import ba.tfb.tasknest.entity.TaskPhoto;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskPhotoRepository extends JpaRepository<TaskPhoto, UUID> {

    List<TaskPhoto> findByTaskIdOrderByPositionAsc(UUID taskId);

    long countByTaskId(UUID taskId);

    @EntityGraph(attributePaths = "task")
    Optional<TaskPhoto> findWithTaskById(UUID id);
}
