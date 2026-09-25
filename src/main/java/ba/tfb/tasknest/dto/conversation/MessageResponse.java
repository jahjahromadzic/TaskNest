package ba.tfb.tasknest.dto.conversation;

import ba.tfb.tasknest.entity.Message;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Samo senderId, bez imena: frontend zna ko je prijavljen i ko je druga strana
 * (iz liste razgovora), pa mu je ID dovoljan da poruku postavi lijevo ili desno.
 * A citanje ID-a sa lijenog proxyja ne pokrece upit - ime bi pokrenulo po jedan
 * za svaku poruku na stranici.
 */
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
