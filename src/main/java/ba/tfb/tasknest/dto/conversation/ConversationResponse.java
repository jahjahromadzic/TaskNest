package ba.tfb.tasknest.dto.conversation;

import ba.tfb.tasknest.entity.enums.ConversationStatus;
import ba.tfb.tasknest.entity.enums.TaskStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        UUID offerId,
        BigDecimal offerPrice,
        String offerMessage,
        UUID taskId,
        String taskTitle,
        TaskStatus taskStatus,
        UUID otherPartyId,
        String otherPartyName,
        ConversationStatus status,
        LocalDateTime lastMessageAt,
        String lastMessage,
        UUID lastMessageSenderId,
        long unreadCount
) {
}
