package ba.tfb.tasknest.dto.notification;

import ba.tfb.tasknest.entity.Notification;
import ba.tfb.tasknest.entity.enums.NotificationType;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        NotificationType type,
        String content,

        /** Task, ponuda ili poruka na koju se notifikacija odnosi; tip govori na sta. */
        UUID relatedEntityId,

        boolean read,
        LocalDateTime createdAt
) {
    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getContent(),
                notification.getRelatedEntityId(),
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
