package ba.tfb.tasknest.service;

import ba.tfb.tasknest.dto.conversation.ConversationResponse;
import ba.tfb.tasknest.dto.conversation.SendMessageRequest;
import ba.tfb.tasknest.entity.Conversation;
import ba.tfb.tasknest.entity.Message;
import ba.tfb.tasknest.entity.Offer;
import ba.tfb.tasknest.entity.Task;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.ConversationStatus;
import ba.tfb.tasknest.exception.BusinessRuleException;
import ba.tfb.tasknest.exception.NotResourceOwnerException;
import ba.tfb.tasknest.exception.ResourceNotFoundException;
import ba.tfb.tasknest.repository.ConversationRepository;
import ba.tfb.tasknest.repository.MessageRepository;
import ba.tfb.tasknest.repository.projection.ConversationUnreadCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    private static final UUID CONVERSATION_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID TASKER_ID = UUID.randomUUID();
    private static final UUID OUTSIDER_ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 15, 12, 0);

    @Mock private ConversationRepository conversationRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private NotificationService notificationService;

    private final Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);

    private ConversationService conversationService;

    @BeforeEach
    void setUp() {
        conversationService = new ConversationService(
                conversationRepository, messageRepository, notificationService, clock);
    }

    @Nested
    class SendMessage {

        @Test
        void sendMessage_notifiesTheClient_whenTheTaskerWrites() {
            // Arrange
            Conversation conversation = aConversation(ConversationStatus.OPEN);
            stubConversation(conversation);
            stubSave();

            // Act
            conversationService.sendMessage(CONVERSATION_ID, TASKER_ID, aMessage());

            // Assert
            ArgumentCaptor<User> recipient = ArgumentCaptor.forClass(User.class);
            verify(notificationService).notifyNewMessage(eq(conversation), recipient.capture());
            assertThat(recipient.getValue().getId()).isEqualTo(CLIENT_ID);
        }

        @Test
        void sendMessage_notifiesTheTasker_whenTheClientWrites() {
            // Arrange
            Conversation conversation = aConversation(ConversationStatus.OPEN);
            stubConversation(conversation);
            stubSave();

            // Act
            conversationService.sendMessage(CONVERSATION_ID, CLIENT_ID, aMessage());

            // Assert
            ArgumentCaptor<User> recipient = ArgumentCaptor.forClass(User.class);
            verify(notificationService).notifyNewMessage(eq(conversation), recipient.capture());
            assertThat(recipient.getValue().getId()).isEqualTo(TASKER_ID);
        }

        @Test
        void sendMessage_recordsTheSender_fromTheCaller() {
            // Arrange
            stubConversation(aConversation(ConversationStatus.OPEN));
            stubSave();

            // Act
            var response = conversationService.sendMessage(CONVERSATION_ID, CLIENT_ID, aMessage());

            // Assert
            assertThat(response.senderId()).isEqualTo(CLIENT_ID);
            assertThat(response.content()).isEqualTo("Kada mozete doci?");
        }

        @Test
        void sendMessage_movesTheConversationToTheTop_byStampingLastMessageAt() {
            // Arrange
            Conversation conversation = aConversation(ConversationStatus.OPEN);
            stubConversation(conversation);
            stubSave();

            // Act
            conversationService.sendMessage(CONVERSATION_ID, CLIENT_ID, aMessage());

            // Assert
            assertThat(conversation.getLastMessageAt()).isEqualTo(NOW);
        }

        @Test
        void sendMessage_throwsBusinessRule_whenConversationIsArchived() {
            // Arrange
            stubConversation(aConversation(ConversationStatus.ARCHIVED));

            // Act + Assert
            assertThatThrownBy(() -> conversationService.sendMessage(CONVERSATION_ID, TASKER_ID, aMessage()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("archived");

            // Assert
            verify(messageRepository, never()).save(any());
            verify(notificationService, never()).notifyNewMessage(any(), any());
        }

        @Test
        void sendMessage_throwsNotOwner_whenCallerIsNotAParticipant() {
            // Arrange
            stubConversation(aConversation(ConversationStatus.OPEN));

            // Act + Assert
            assertThatThrownBy(() -> conversationService.sendMessage(CONVERSATION_ID, OUTSIDER_ID, aMessage()))
                    .isInstanceOf(NotResourceOwnerException.class);
        }

        @Test
        void sendMessage_throwsNotFound_whenConversationDoesNotExist() {
            // Arrange
            when(conversationRepository.findWithParticipantsById(CONVERSATION_ID)).thenReturn(Optional.empty());

            // Act + Assert
            assertThatThrownBy(() -> conversationService.sendMessage(CONVERSATION_ID, CLIENT_ID, aMessage()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    class ReadingAndMarking {

        @Test
        void getMessages_throwsNotOwner_whenCallerIsNotAParticipant() {
            // Arrange
            stubConversation(aConversation(ConversationStatus.OPEN));

            // Act + Assert
            assertThatThrownBy(() -> conversationService.getMessages(
                    CONVERSATION_ID, OUTSIDER_ID, PageRequest.of(0, 20)))
                    .isInstanceOf(NotResourceOwnerException.class);
        }

        @Test
        void getMessages_isAllowed_whenConversationIsArchived() {
            // Arrange
            Conversation archived = aConversation(ConversationStatus.ARCHIVED);
            stubConversation(archived);
            when(messageRepository.findByConversationOrderByCreatedAtAsc(eq(archived), any(Pageable.class)))
                    .thenReturn(Page.empty());

            // Act
            var page = conversationService.getMessages(CONVERSATION_ID, CLIENT_ID, PageRequest.of(0, 20));

            // Assert
            assertThat(page).isEmpty();
        }

        @Test
        void markAsRead_clearsTheBellForThisConversation() {
            // Arrange
            Conversation conversation = aConversation(ConversationStatus.OPEN);
            stubConversation(conversation);

            // Act
            conversationService.markAsRead(CONVERSATION_ID, CLIENT_ID);

            // Assert
            verify(notificationService).clearNewMessageNotifications(conversation, CLIENT_ID);
        }

        @Test
        void markAsRead_throwsNotOwner_whenCallerIsNotAParticipant() {
            // Arrange
            stubConversation(aConversation(ConversationStatus.OPEN));

            // Act + Assert
            assertThatThrownBy(() -> conversationService.markAsRead(CONVERSATION_ID, OUTSIDER_ID))
                    .isInstanceOf(NotResourceOwnerException.class);
        }
    }

    @Nested
    class Listing {

        @Test
        void getMyConversations_showsTheOtherParty_dependingOnWhoAsks() {
            // Arrange
            Conversation conversation = aConversation(ConversationStatus.OPEN);
            when(conversationRepository.findAllByParticipant(eq(CLIENT_ID), any()))
                    .thenReturn(new PageImpl<>(List.of(conversation)));
            when(conversationRepository.findAllByParticipant(eq(TASKER_ID), any()))
                    .thenReturn(new PageImpl<>(List.of(conversation)));

            // Act
            ConversationResponse seenByClient =
                    conversationService.getMyConversations(CLIENT_ID, PageRequest.of(0, 20)).getContent().getFirst();
            ConversationResponse seenByTasker =
                    conversationService.getMyConversations(TASKER_ID, PageRequest.of(0, 20)).getContent().getFirst();

            // Assert
            assertThat(seenByClient.otherPartyId()).isEqualTo(TASKER_ID);
            assertThat(seenByTasker.otherPartyId()).isEqualTo(CLIENT_ID);
        }

        @Test
        void getMyConversations_reportsZeroUnread_whenTheGroupedQueryOmitsAConversation() {
            // Arrange
            Conversation withUnread = aConversation(ConversationStatus.OPEN);
            Conversation allRead = aConversation(ConversationStatus.OPEN);
            allRead.setId(UUID.randomUUID());
            when(conversationRepository.findAllByParticipant(eq(CLIENT_ID), any()))
                    .thenReturn(new PageImpl<>(List.of(withUnread, allRead)));
            when(messageRepository.countUnreadByConversation(eq(CLIENT_ID), anyCollection()))
                    .thenReturn(List.of(new ConversationUnreadCount(CONVERSATION_ID, 3)));

            // Act
            List<ConversationResponse> page =
                    conversationService.getMyConversations(CLIENT_ID, PageRequest.of(0, 20)).getContent();

            // Assert
            assertThat(page).extracting(ConversationResponse::unreadCount).containsExactly(3L, 0L);
        }

        @Test
        void getMyConversations_skipsTheCountQuery_whenThePageIsEmpty() {
            // Arrange
            when(conversationRepository.findAllByParticipant(eq(CLIENT_ID), any()))
                    .thenReturn(Page.empty());

            // Act
            var page = conversationService.getMyConversations(CLIENT_ID, PageRequest.of(0, 20));

            // Assert
            assertThat(page).isEmpty();
            verify(messageRepository, never()).countUnreadByConversation(any(), anyCollection());
        }
    }

    private void stubConversation(Conversation conversation) {
        when(conversationRepository.findWithParticipantsById(CONVERSATION_ID))
                .thenReturn(Optional.of(conversation));
    }

    private void stubSave() {
        when(messageRepository.save(any(Message.class))).thenAnswer(call -> call.getArgument(0));
    }

    private SendMessageRequest aMessage() {
        return new SendMessageRequest("Kada mozete doci?");
    }

    private User aUser(UUID id, String firstName) {
        User user = new User();
        user.setId(id);
        user.setFirstName(firstName);
        user.setLastName("Test");
        return user;
    }

    private Conversation aConversation(ConversationStatus status) {
        Task task = new Task();
        task.setId(UUID.randomUUID());
        task.setTitle("Popravka slavine");
        task.setClient(aUser(CLIENT_ID, "Amra"));

        Offer offer = new Offer();
        offer.setId(UUID.randomUUID());
        offer.setTask(task);
        offer.setTasker(aUser(TASKER_ID, "Emir"));

        Conversation conversation = new Conversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setOffer(offer);
        conversation.setStatus(status);
        return conversation;
    }
}
