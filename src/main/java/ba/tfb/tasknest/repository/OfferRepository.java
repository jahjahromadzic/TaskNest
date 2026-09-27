package ba.tfb.tasknest.repository;

import ba.tfb.tasknest.dto.offer.TaskOfferResponse;
import ba.tfb.tasknest.entity.Offer;
import ba.tfb.tasknest.entity.Task;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.OfferStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OfferRepository extends JpaRepository<Offer, UUID> {

    @EntityGraph(attributePaths = {"task", "tasker"})
    List<Offer> findByTask(Task task);

    @EntityGraph(attributePaths = {"task", "tasker"})
    List<Offer> findByTasker(User tasker);

    Optional<Offer> findByTaskAndTasker(Task task, User tasker);

    List<Offer> findByTaskAndStatus(Task task, OfferStatus status);

    @Query("""
        select new ba.tfb.tasknest.dto.offer.TaskOfferResponse(
            o.id, o.price, o.message, o.status, o.createdAt,
            u.id, concat(u.firstName, ' ', u.lastName), p.headline,
            coalesce(p.verified, false), p.averageRating, coalesce(p.completedJobsCount, 0),
            (select count(r) from Review r where r.reviewee = u))
        from Offer o
        join o.tasker u
        left join TaskerProfile p on p.user = u
        where o.task.id = :taskId
        order by o.createdAt
        """)
    List<TaskOfferResponse> findWithTaskerByTaskId(@Param("taskId") UUID taskId);
}
