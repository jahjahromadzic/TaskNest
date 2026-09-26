package ba.tfb.tasknest.repository.projection;

import java.util.UUID;

public record ConversationUnreadCount(
        UUID conversationId,
        long unread
) {
}
