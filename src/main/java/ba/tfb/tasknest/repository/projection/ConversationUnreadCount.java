package ba.tfb.tasknest.repository.projection;

import java.util.UUID;

/**
 * Broj neprocitanih poruka u jednom razgovoru.
 * <p>
 * Postoji da lista razgovora ne bi zvala count u petlji: jedan grupisani upit za
 * cijelu stranicu umjesto po jednog za svaki razgovor.
 */
public record ConversationUnreadCount(
        UUID conversationId,
        long unread
) {
}
