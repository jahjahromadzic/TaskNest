package ba.tfb.tasknest.repository;

import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.entity.Task;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.TaskStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    /**
     * Cita task uz OPTIMISTIC lock, tj. provjerava mu verziju na kraju transakcije
     * i kad ga transakcija nije mijenjala. Koristi se tamo gdje se odluka donosi na
     * osnovu stanja taska, a upisuje se u drugu tabelu - obican findById tu ne bi
     * primijetio da je task u medjuvremenu promijenjen.
     */
    @Lock(LockModeType.OPTIMISTIC)
    Optional<Task> findWithOptimisticLockById(UUID id);

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


    List<Task> findByStatusAndExpiresAtBefore(TaskStatus status, LocalDateTime moment);
}
