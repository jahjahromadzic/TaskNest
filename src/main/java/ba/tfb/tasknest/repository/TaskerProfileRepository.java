package ba.tfb.tasknest.repository;

import ba.tfb.tasknest.entity.TaskerProfile;
import ba.tfb.tasknest.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskerProfileRepository extends JpaRepository<TaskerProfile, UUID> {

    Optional<TaskerProfile> findByUser(User user);


    @Query("""
            select distinct tp from TaskerProfile tp
            join tp.categories c
            join tp.municipalities m
            where c.id = :categoryId and m.id = :municipalityId
            """)
    List<TaskerProfile> findByCategoryAndMunicipality(@Param("categoryId") UUID categoryId,
                                                      @Param("municipalityId") UUID municipalityId);
}
