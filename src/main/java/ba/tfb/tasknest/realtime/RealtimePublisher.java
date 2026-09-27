package ba.tfb.tasknest.realtime;

import ba.tfb.tasknest.dto.conversation.MessageResponse;
import ba.tfb.tasknest.dto.notification.NotificationResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class RealtimePublisher {

    public static final String MESSAGES = "/queue/messages";
    public static final String READS = "/queue/reads";
    public static final String NOTIFICATIONS = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    @TransactionalEventListener
    public void onMessageSent(RealtimeEvents.MessageSent event) {
        MessageEvent payload = new MessageEvent(
                event.message().getConversation().getId(), MessageResponse.from(event.message()));

        send(event.recipientId(), MESSAGES, payload);
        send(event.senderId(), MESSAGES, payload);
    }

    @TransactionalEventListener
    public void onMessagesRead(RealtimeEvents.MessagesRead event) {
        send(event.senderId(), READS, new ReadEvent(event.conversationId(), event.readerId(), event.readAt()));
    }

    @TransactionalEventListener
    public void onNotificationCreated(RealtimeEvents.NotificationCreated event) {
        send(event.notification().getRecipient().getId(), NOTIFICATIONS,
                NotificationResponse.from(event.notification()));
    }

    private void send(UUID userId, String destination, Object payload) {
        try {
            messagingTemplate.convertAndSendToUser(userId.toString(), destination, payload);
        } catch (MessagingException e) {
            log.warn("Could not push {} to user {}: {}", destination, userId, e.getMessage());
        }
    }

    public record MessageEvent(UUID conversationId, MessageResponse message) {
    }

    public record ReadEvent(UUID conversationId, UUID readerId, LocalDateTime readAt) {
    }
}
