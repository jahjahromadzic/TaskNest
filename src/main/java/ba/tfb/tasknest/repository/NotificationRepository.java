package ba.tfb.tasknest.repository;

import ba.tfb.tasknest.entity.Notification;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByRecipientOrderByCreatedAtDesc(User recipient);

    Page<Notification> findByRecipientOrderByCreatedAtDesc(User recipient, Pageable pageable);

    long countByRecipientAndReadFalse(User recipient);

    boolean existsByRecipientAndTypeAndRelatedEntityIdAndReadFalse(
            User recipient, NotificationType type, UUID relatedEntityId);

    @Modifying
    @Query("""
            update Notification n set n.read = true
            where n.recipient.id = :recipientId
              and n.type = :type
              and n.relatedEntityId = :relatedEntityId
              and n.read = false
            """)
    int markReadFor(@Param("recipientId") UUID recipientId,
                    @Param("type") NotificationType type,
                    @Param("relatedEntityId") UUID relatedEntityId);
}
