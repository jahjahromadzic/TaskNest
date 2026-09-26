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

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final NotificationService notificationService;
    private final Clock clock;

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

    @Transactional(readOnly = true)
    public Page<MessageResponse> getMessages(UUID conversationId, UUID userId, Pageable pageable) {
        Conversation conversation = loadForParticipant(conversationId, userId);

        return messageRepository.findByConversationOrderByCreatedAtAsc(conversation, pageable)
                .map(MessageResponse::from);
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

        return MessageResponse.from(saved);
    }

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
