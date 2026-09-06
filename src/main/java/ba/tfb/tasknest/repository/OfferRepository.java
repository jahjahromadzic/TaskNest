package ba.tfb.tasknest.repository;

import ba.tfb.tasknest.entity.Offer;
import ba.tfb.tasknest.entity.Task;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.OfferStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfferRepository extends JpaRepository<Offer, UUID> {

    /**
     * task i tasker se dovlace odmah jer ih OfferResponse uvijek cita; bez ovoga je
     * mapiranje liste jedan upit po ponudi.
     */
    @EntityGraph(attributePaths = {"task", "tasker"})
    List<Offer> findByTask(Task task);

    @EntityGraph(attributePaths = {"task", "tasker"})
    List<Offer> findByTasker(User tasker);

    Optional<Offer> findByTaskAndTasker(Task task, User tasker);


    List<Offer> findByTaskAndStatus(Task task, OfferStatus status);
}
