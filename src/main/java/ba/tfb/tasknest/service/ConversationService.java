package ba.tfb.tasknest.service;

import ba.tfb.tasknest.dto.conversation.ConversationResponse;
import ba.tfb.tasknest.dto.conversation.MessageResponse;
import ba.tfb.tasknest.dto.conversation.SendMessageRequest;
import ba.tfb.tasknest.entity.Conversation;
import ba.tfb.tasknest.entity.Message;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.ConversationStatus;
import ba.tfb.tasknest.exception.BusinessRuleException;
import ba.tfb.tasknest.exception.NotResourceOwnerException;
import ba.tfb.tasknest.exception.ResourceNotFoundException;
import ba.tfb.tasknest.repository.ConversationRepository;
import ba.tfb.tasknest.repository.MessageRepository;
import ba.tfb.tasknest.repository.projection.ConversationUnreadCount;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Poruke izmedju klijenta i taskera, u okviru jedne ponude.
 * <p>
 * Ucesnik razgovora je tasker s ponude ili klijent s posla te ponude - niko
 * drugi, i bez obzira na role. Ista osoba moze biti klijent u jednom razgovoru i
 * tasker u drugom.
 */
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final NotificationService notificationService;
    private final Clock clock;

    /**
     * Razgovori korisnika, najnovija aktivnost prvo, s brojem neprocitanih.
     * <p>
     * Neprocitane se broje jednim grupisanim upitom za cijelu stranicu, ne po
     * jednim za svaki razgovor.
     */
    @Transactional(readOnly = true)
    public Page<ConversationResponse> getMyConversations(UUID userId, Pageable pageable) {
        Page<Conversation> page = conversationRepository.findAllByParticipant(userId, pageable);

        if (page.isEmpty()) {
            return page.map(conversation -> toResponse(conversation, userId, 0L));
        }

        List<UUID> ids = page.getContent().stream().map(Conversation::getId).toList();
        Map<UUID, Long> unread = messageRepository.countUnreadByConversation(userId, ids).stream()
                .collect(Collectors.toMap(
                        ConversationUnreadCount::conversationId, ConversationUnreadCount::unread));

        return page.map(conversation ->
                toResponse(conversation, userId, unread.getOrDefault(conversation.getId(), 0L)));
    }

    /** Poruke razgovora, najstarija prvo. Citanje ne mijenja stanje - za to je markAsRead. */
    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessages(UUID conversationId, UUID userId, Pageable pageable) {
        Conversation conversation = loadForParticipant(conversationId, userId);

        return messageRepository.findByConversationOrderByCreatedAtAsc(conversation, pageable)
                .map(MessageResponse::from);
    }

    /**
     * Salje poruku drugoj strani.
     * <p>
     * Arhiviran razgovor je samo za citanje: ponuda je mrtva ili je posao
     * zavrsen, pa nema sta da se dogovara - a bez ovoga bi odbijeni tasker mogao
     * neograniceno pisati klijentu koji ga je odbio.
     */
    @Transactional
    public MessageResponse sendMessage(UUID conversationId, UUID senderId, SendMessageRequest request) {
        Conversation conversation = loadForParticipant(conversationId, senderId);

        if (conversation.getStatus() == ConversationStatus.ARCHIVED) {
            throw new BusinessRuleException("This conversation is archived and no longer accepts messages");
        }

        User sender = participant(conversation, senderId);
        User recipient = otherParty(conversation, senderId);

        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(request.content());
        Message saved = messageRepository.save(message);

        // Dvije paralelne poruke obje postave ovo na priblizno isti trenutak, i
        // zadnja pobjedjuje - bezopasno, jer se nista ne racuna iz prethodne
        // vrijednosti. Zato ovdje nema loka, za razliku od prosjeka ocjena.
        conversation.setLastMessageAt(LocalDateTime.now(clock));

        notificationService.notifyNewMessage(conversation, recipient);

        return MessageResponse.from(saved);
    }

    /**
     * Oznacava procitanim poruke druge strane i gasi zvonce za ovaj razgovor.
     * Eksplicitan poziv, ne posljedica citanja: GET ostaje bez efekata.
     *
     * @return koliko je poruka oznaceno
     */
    @Transactional
    public int markAsRead(UUID conversationId, UUID readerId) {
        Conversation conversation = loadForParticipant(conversationId, readerId);

        int marked = messageRepository.markReadByRecipient(conversation, readerId, LocalDateTime.now(clock));
        notificationService.clearNewMessageNotifications(conversation, readerId);

        return marked;
    }

    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return messageRepository.countUnreadForUser(userId);
    }

    // ---------- helpers ----------

    private Conversation loadForParticipant(UUID conversationId, UUID userId) {
        Conversation conversation = conversationRepository.findWithParticipantsById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", conversationId));

        if (!isParticipant(conversation, userId)) {
            throw new NotResourceOwnerException("You are not a participant in this conversation");
        }

        return conversation;
    }

    private static boolean isParticipant(Conversation conversation, UUID userId) {
        return taskerOf(conversation).getId().equals(userId)
                || clientOf(conversation).getId().equals(userId);
    }

    private static User participant(Conversation conversation, UUID userId) {
        return taskerOf(conversation).getId().equals(userId)
                ? taskerOf(conversation)
                : clientOf(conversation);
    }

    private static User otherParty(Conversation conversation, UUID userId) {
        return taskerOf(conversation).getId().equals(userId)
                ? clientOf(conversation)
                : taskerOf(conversation);
    }

    private static User taskerOf(Conversation conversation) {
        return conversation.getOffer().getTasker();
    }

    private static User clientOf(Conversation conversation) {
        return conversation.getOffer().getTask().getClient();
    }

    private static ConversationResponse toResponse(Conversation conversation, UUID viewerId, long unread) {
        User other = otherParty(conversation, viewerId);

        return new ConversationResponse(
                conversation.getId(),
                conversation.getOffer().getId(),
                conversation.getOffer().getTask().getId(),
                conversation.getOffer().getTask().getTitle(),
                other.getId(),
                other.getFirstName() + " " + other.getLastName(),
                conversation.getStatus(),
                conversation.getLastMessageAt(),
                unread
        );
    }
}
