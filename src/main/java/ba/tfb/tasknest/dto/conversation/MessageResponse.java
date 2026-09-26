package ba.tfb.tasknest.dto.conversation;

import ba.tfb.tasknest.entity.Message;

import java.time.LocalDateTime;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        UUID senderId,
        String content,
        LocalDateTime readAt,
        LocalDateTime createdAt
) {
    public static MessageResponse from(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getSender().getId(),
                message.getContent(),
                message.getReadAt(),
                message.getCreatedAt()
        );
    }
}
