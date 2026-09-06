package ba.tfb.tasknest.repository;

import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.entity.Task;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.TaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByClient(User client);

    Page<Task> findByClient(User client, Pageable pageable);

    List<Task> findByStatus(TaskStatus status);

    Page<Task> findByStatus(TaskStatus status, Pageable pageable);

    List<Task> findByMunicipalityAndCategoryAndStatus(Municipality municipality,
                                                      Category category,
                                                      TaskStatus status);

    Page<Task> findByMunicipalityAndCategoryAndStatus(Municipality municipality,
                                                     Category category,
                                                     TaskStatus status,
                                                     Pageable pageable);

    /** Kandidati za prelazak u EXPIRED - poziva se sa statusom PUBLISHED iz schedulera. */
    List<Task> findByStatusAndExpiresAtBefore(TaskStatus status, LocalDateTime moment);
}
