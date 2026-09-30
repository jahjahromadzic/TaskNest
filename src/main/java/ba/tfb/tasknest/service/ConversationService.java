package ba.tfb.tasknest.service;

import ba.tfb.tasknest.dto.conversation.ConversationResponse;
import ba.tfb.tasknest.dto.conversation.ConversationRole;
import ba.tfb.tasknest.dto.conversation.MessageResponse;
import ba.tfb.tasknest.dto.conversation.SendMessageRequest;
import ba.tfb.tasknest.entity.Conversation;
import ba.tfb.tasknest.entity.Message;
import ba.tfb.tasknest.entity.Offer;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.ConversationStatus;
import ba.tfb.tasknest.exception.BusinessRuleException;
import ba.tfb.tasknest.exception.NotResourceOwnerException;
import ba.tfb.tasknest.exception.ResourceNotFoundException;
import ba.tfb.tasknest.repository.ConversationRepository;
import ba.tfb.tasknest.repository.MessageRepository;
import ba.tfb.tasknest.repository.projection.ConversationUnreadCount;
import ba.tfb.tasknest.realtime.RealtimeEvents;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<ConversationResponse> getMyConversations(UUID userId, Pageable pageable) {
        Page<Conversation> page = conversationRepository.findAllByParticipant(userId, pageable);
        List<ConversationResponse> described = describe(page.getContent(), userId);
        return new PageImpl<>(described, page.getPageable(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ConversationResponse getForOffer(UUID offerId, UUID userId) {
        Conversation conversation = conversationRepository.findWithParticipantsByOfferId(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation for offer", offerId));

        if (!isParticipant(conversation, userId)) {
            throw new NotResourceOwnerException("You are not a participant in this conversation");
        }

        return describe(List.of(conversation), userId).getFirst();
    }

    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessages(UUID conversationId, UUID userId, Pageable pageable) {
        Conversation conversation = loadForParticipant(conversationId, userId);

        Page<Message> newestFirst = messageRepository.findByConversationOrderByCreatedAtDesc(conversation, pageable);
        List<MessageResponse> chronological = newestFirst.getContent().reversed().stream()
                .map(MessageResponse::from)
                .toList();

        return new PageImpl<>(chronological, newestFirst.getPageable(), newestFirst.getTotalElements());
    }

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

        conversation.setLastMessageAt(LocalDateTime.now(clock));

        notificationService.notifyNewMessage(conversation, recipient);
        eventPublisher.publishEvent(new RealtimeEvents.MessageSent(saved, sender.getId(), recipient.getId()));

        return MessageResponse.from(saved);
    }

    @Transactional
    public int markAsRead(UUID conversationId, UUID readerId) {
        Conversation conversation = loadForParticipant(conversationId, readerId);

        LocalDateTime now = LocalDateTime.now(clock);
        int marked = messageRepository.markReadByRecipient(conversation, readerId, now);
        notificationService.clearNewMessageNotifications(conversation, readerId);

        if (marked > 0) {
            eventPublisher.publishEvent(new RealtimeEvents.MessagesRead(
                    conversationId, readerId, otherParty(conversation, readerId).getId(), now));
        }

        return marked;
    }

    @Transactional(readOnly = true)
    public long countUnread(UUID userId) {
        return messageRepository.countUnreadForUser(userId);
    }

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

    private List<ConversationResponse> describe(List<Conversation> conversations, UUID viewerId) {
        if (conversations.isEmpty()) {
            return List.of();
        }

        List<UUID> ids = conversations.stream().map(Conversation::getId).toList();
        Map<UUID, Long> unread = messageRepository.countUnreadByConversation(viewerId, ids).stream()
                .collect(Collectors.toMap(
                        ConversationUnreadCount::conversationId, ConversationUnreadCount::unread));
        Map<UUID, Message> latest = messageRepository.findLatestIn(ids).stream()
                .collect(Collectors.toMap(
                        message -> message.getConversation().getId(), message -> message, (first, second) -> first));

        return conversations.stream()
                .map(conversation -> toResponse(conversation, viewerId,
                        unread.getOrDefault(conversation.getId(), 0L), latest.get(conversation.getId())))
                .toList();
    }

    private static ConversationResponse toResponse(Conversation conversation, UUID viewerId, long unread, Message last) {
        User other = otherParty(conversation, viewerId);
        Offer offer = conversation.getOffer();

        return new ConversationResponse(
                conversation.getId(),
                offer.getId(),
                offer.getPrice(),
                offer.getMessage(),
                offer.getStatus(),
                offer.getTask().getId(),
                offer.getTask().getTitle(),
                offer.getTask().getStatus(),
                other.getId(),
                other.getFirstName() + " " + other.getLastName(),
                clientOf(conversation).getId().equals(viewerId) ? ConversationRole.CLIENT : ConversationRole.TASKER,
                conversation.getStatus(),
                conversation.getLastMessageAt(),
                last == null ? null : last.getContent(),
                last == null ? null : last.getSender().getId(),
                unread
        );
    }
}
