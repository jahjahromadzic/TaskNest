package ba.tfb.tasknest.repository;

import ba.tfb.tasknest.entity.TaskerProfile;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.repository.projection.TaskerNotificationTarget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskerProfileRepository extends JpaRepository<TaskerProfile, UUID> {

    Optional<TaskerProfile> findByUser(User user);

    /**
     * Srz matchinga: taskeri koji pokrivaju i datu kategoriju i datu opstinu.
     * <p>
     * Vraca projekciju, ne entitete - pozivalac je notifikacijski tok koji
     * iterira rezultat, pa bi TaskerProfile znacio tri dodatna upita po taskeru.
     * <p>
     * {@code c.active = true} je namjerno OVDJE, a ne u ciscenju spojne tabele
     * pri deaktivaciji kategorije: red u tasker_categories ostaje, pa ponovna
     * aktivacija kategorije sama vraca prethodni izbor taskera. Ciscenjem bi se
     * izbor nepovratno izgubio na jedan admin klik.
     */
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
            """)
    List<TaskerNotificationTarget> findNotificationTargets(@Param("categoryId") UUID categoryId,
                                                           @Param("municipalityId") UUID municipalityId);
}
