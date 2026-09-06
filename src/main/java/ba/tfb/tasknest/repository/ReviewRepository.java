package ba.tfb.tasknest.repository;

import ba.tfb.tasknest.entity.Review;
import ba.tfb.tasknest.entity.Task;
import ba.tfb.tasknest.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    List<Review> findByReviewee(User reviewee);

    boolean existsByTaskAndReviewer(Task task, User reviewer);

    @Query("select avg(r.rating) from Review r where r.reviewee.id = :revieweeId")
    Optional<Double> findAverageRatingByReviewee(@Param("revieweeId") UUID revieweeId);
}
