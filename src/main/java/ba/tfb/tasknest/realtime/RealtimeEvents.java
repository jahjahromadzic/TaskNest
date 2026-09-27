package ba.tfb.tasknest.realtime;

import ba.tfb.tasknest.entity.Message;
import ba.tfb.tasknest.entity.Notification;

import java.time.LocalDateTime;
import java.util.UUID;

public final class RealtimeEvents {

    private RealtimeEvents() {
    }

    public record MessageSent(Message message, UUID senderId, UUID recipientId) {
    }

    public record MessagesRead(UUID conversationId, UUID readerId, UUID senderId, LocalDateTime readAt) {
    }

    public record NotificationCreated(Notification notification) {
    }
}
