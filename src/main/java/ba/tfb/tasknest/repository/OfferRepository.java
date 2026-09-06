package ba.tfb.tasknest.repository;

import ba.tfb.tasknest.entity.Offer;
import ba.tfb.tasknest.entity.Task;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.OfferStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfferRepository extends JpaRepository<Offer, UUID> {

    List<Offer> findByTask(Task task);

    List<Offer> findByTasker(User tasker);

    Optional<Offer> findByTaskAndTasker(Task task, User tasker);

    /** Ostale ponude na tasku - potrebno kod prihvatanja, kad idu u REJECTED. */
    List<Offer> findByTaskAndStatus(Task task, OfferStatus status);
}
