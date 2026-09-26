package ba.tfb.tasknest.dto.conversation;

import ba.tfb.tasknest.entity.enums.ConversationStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        UUID offerId,
        UUID taskId,
        String taskTitle,
        UUID otherPartyId,
        String otherPartyName,
        ConversationStatus status,
        LocalDateTime lastMessageAt,
        long unreadCount
) {
}
