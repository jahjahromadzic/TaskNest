package ba.tfb.tasknest.repository;

import ba.tfb.tasknest.entity.Review;
import ba.tfb.tasknest.entity.Task;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.repository.projection.RatingSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    List<Review> findByReviewee(User reviewee);

    @EntityGraph(attributePaths = {"task", "reviewer", "reviewee"})
    Page<Review> findByRevieweeOrderByCreatedAtDesc(User reviewee, Pageable pageable);

    boolean existsByTaskAndReviewer(Task task, User reviewer);

    @EntityGraph(attributePaths = {"task", "reviewer", "reviewee"})
    List<Review> findByTaskIdOrderByCreatedAtAsc(UUID taskId);

    @Query("select avg(r.rating) from Review r where r.reviewee.id = :revieweeId and r.task.client.id <> :revieweeId")
    Optional<Double> findAverageRatingAsTasker(@Param("revieweeId") UUID revieweeId);

    @EntityGraph(attributePaths = {"task", "reviewer", "reviewee"})
    @Query(value = """
        select r from Review r
        where r.reviewee.id = :userId and r.task.client.id = :userId
        order by r.createdAt desc
        """,
        countQuery = """
        select count(r) from Review r
        where r.reviewee.id = :userId and r.task.client.id = :userId
        """)
    Page<Review> findReceivedAsClient(@Param("userId") UUID userId, Pageable pageable);

    @EntityGraph(attributePaths = {"task", "reviewer", "reviewee"})
    @Query(value = """
        select r from Review r
        where r.reviewee.id = :userId and r.task.client.id <> :userId
        order by r.createdAt desc
        """,
        countQuery = """
        select count(r) from Review r
        where r.reviewee.id = :userId and r.task.client.id <> :userId
        """)
    Page<Review> findReceivedAsTasker(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
        select new ba.tfb.tasknest.repository.projection.RatingSummary(avg(r.rating), count(r))
        from Review r
        where r.reviewee.id = :userId and r.task.client.id = :userId
        """)
    RatingSummary summarizeReceivedAsClient(@Param("userId") UUID userId);
}
