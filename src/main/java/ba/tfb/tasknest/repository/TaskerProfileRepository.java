package ba.tfb.tasknest.repository;

import ba.tfb.tasknest.entity.TaskerProfile;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.repository.projection.TaskerNotificationTarget;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskerProfileRepository extends JpaRepository<TaskerProfile, UUID> {

    Optional<TaskerProfile> findByUser(User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TaskerProfile> findWithWriteLockByUser(User user);

    @Query("""
            select distinct new ba.tfb.tasknest.repository.projection.TaskerNotificationTarget(
                    u.id,
                    u.email,
                    concat(coalesce(u.firstName, ''), ' ', coalesce(u.lastName, '')))
            from TaskerProfile tp
            join tp.user u
            join tp.categories c
            join tp.municipalities m
            where c.id = :categoryId
              and c.active = true
              and m.id = :municipalityId
              and u.id <> :clientId
              and u.accountStatus = ba.tfb.tasknest.entity.enums.AccountStatus.ACTIVE
            """)
    List<TaskerNotificationTarget> findNotificationTargets(@Param("categoryId") UUID categoryId,
                                                           @Param("municipalityId") UUID municipalityId,
                                                           @Param("clientId") UUID clientId);
}
