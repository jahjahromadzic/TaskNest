package ba.tfb.tasknest.service;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.dto.conversation.ConversationResponse;
import ba.tfb.tasknest.dto.conversation.SendMessageRequest;
import ba.tfb.tasknest.dto.offer.CreateOfferRequest;
import ba.tfb.tasknest.dto.task.CreateTaskRequest;
import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.entity.Conversation;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.entity.Notification;
import ba.tfb.tasknest.entity.Offer;
import ba.tfb.tasknest.entity.enums.ConversationStatus;
import ba.tfb.tasknest.entity.enums.NotificationType;
import ba.tfb.tasknest.exception.BusinessRuleException;
import ba.tfb.tasknest.exception.NotResourceOwnerException;
import ba.tfb.tasknest.repository.CategoryRepository;
import ba.tfb.tasknest.repository.ConversationRepository;
import ba.tfb.tasknest.repository.MessageRepository;
import ba.tfb.tasknest.repository.MunicipalityRepository;
import ba.tfb.tasknest.repository.NotificationRepository;
import ba.tfb.tasknest.repository.OfferRepository;
import ba.tfb.tasknest.repository.RefreshTokenRepository;
import ba.tfb.tasknest.repository.ReviewRepository;
import ba.tfb.tasknest.repository.TaskRepository;
import ba.tfb.tasknest.repository.TaskerProfileRepository;
import ba.tfb.tasknest.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationIntegrationTest extends AbstractIntegrationTest {

    @Autowired private AuthService authService;
    @Autowired private TaskService taskService;
    @Autowired private OfferService offerService;
    @Autowired private ConversationService conversationService;
    @Autowired private TransactionTemplate transactionTemplate;

    @Autowired private UserRepository userRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private OfferRepository offerRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MunicipalityRepository municipalityRepository;
    @Autowired private TaskerProfileRepository taskerProfileRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private NotificationRepository notificationRepository;

    private Category category;
    private Municipality municipality;
    private UUID clientId;
    private UUID taskerId;
    private UUID otherTaskerId;

    @BeforeEach
    void setUp() {
        category = categoryRepository.findAll().getFirst();
        municipality = municipalityRepository.findAll().getFirst();

        clientId = register("chat.client@test.ba");
        taskerId = register("chat.tasker@test.ba");
        otherTaskerId = register("chat.tasker2@test.ba");
        authService.activateTaskerRole(taskerId);
        authService.activateTaskerRole(otherTaskerId);
    }

    @AfterEach
    void tearDown() {
        messageRepository.deleteAll();
        reviewRepository.deleteAll();
        notificationRepository.deleteAll();
        conversationRepository.deleteAll();

        transactionTemplate.executeWithoutResult(status ->
                taskRepository.findAll().forEach(task -> task.setAcceptedOffer(null)));

        offerRepository.deleteAll();
        taskRepository.deleteAll();
        taskerProfileRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Nested
    class Lifecycle {

        @Test
        @DisplayName("Submitting an offer opens a conversation for it")
        void submitOffer_opensAConversation() {
            // Arrange + Act
            UUID offerId = submitOffer(publishedTask(), taskerId);

            // Assert
            assertThat(conversationStatusFor(offerId)).isEqualTo(ConversationStatus.OPEN);
        }

        @Test
        @DisplayName("Withdrawing an offer archives its conversation")
        void withdrawOffer_archivesTheConversation() {
            // Arrange
            UUID offerId = submitOffer(publishedTask(), taskerId);

            // Act
            offerService.withdrawOffer(offerId, taskerId);

            // Assert
            assertThat(conversationStatusFor(offerId)).isEqualTo(ConversationStatus.ARCHIVED);
        }

        @Test
        @DisplayName("Accepting one offer archives the others' conversations and keeps its own open")
        void acceptOffer_archivesOnlyTheRejectedConversations() {
            // Arrange
            UUID taskId = publishedTask();
            UUID accepted = submitOffer(taskId, taskerId);
            UUID rejected = submitOffer(taskId, otherTaskerId);

            // Act
            offerService.acceptOffer(accepted, clientId);

            // Assert
            assertThat(conversationStatusFor(accepted)).isEqualTo(ConversationStatus.OPEN);
            assertThat(conversationStatusFor(rejected)).isEqualTo(ConversationStatus.ARCHIVED);
        }

        @Test
        @DisplayName("Closing the task archives the conversation of the accepted offer")
        void closeTask_archivesTheConversation() {
            // Arrange
            UUID taskId = publishedTask();
            UUID offerId = submitOffer(taskId, taskerId);
            offerService.acceptOffer(offerId, clientId);
            taskService.startTask(taskId, taskerId);
            taskService.completeTask(taskId, taskerId);
            assertThat(conversationStatusFor(offerId)).isEqualTo(ConversationStatus.OPEN);

            // Act
            taskService.closeTask(taskId, clientId);

            // Assert
            assertThat(conversationStatusFor(offerId)).isEqualTo(ConversationStatus.ARCHIVED);
        }

        @Test
        @DisplayName("Cancelling the task archives every open conversation on it")
        void cancelTask_archivesTheConversations() {
            // Arrange
            UUID taskId = publishedTask();
            UUID offerId = submitOffer(taskId, taskerId);

            // Act
            taskService.cancelTask(taskId, clientId);

            // Assert
            assertThat(conversationStatusFor(offerId)).isEqualTo(ConversationStatus.ARCHIVED);
        }
    }

    @Nested
    class Messaging {

        @Test
        @DisplayName("Both parties can exchange messages before the offer is accepted")
        void sendMessage_worksBeforeAcceptance() {
            // Arrange
            UUID conversationId = conversationOf(submitOffer(publishedTask(), taskerId));

            // Act
            conversationService.sendMessage(conversationId, clientId, text("Kada mozete doci?"));
            conversationService.sendMessage(conversationId, taskerId, text("Sutra u 10."));

            // Assert
            var messages = conversationService.getMessages(conversationId, clientId, PageRequest.of(0, 50));
            assertThat(messages.getContent())
                    .extracting(m -> m.content())
                    .containsExactly("Kada mozete doci?", "Sutra u 10.");
        }

        @Test
        @DisplayName("An archived conversation stays readable but accepts no new messages")
        void sendMessage_isRejected_whenConversationIsArchived() {
            // Arrange
            UUID offerId = submitOffer(publishedTask(), taskerId);
            UUID conversationId = conversationOf(offerId);
            conversationService.sendMessage(conversationId, taskerId, text("Evo ponude."));
            offerService.withdrawOffer(offerId, taskerId);

            // Act + Assert
            assertThatThrownBy(() -> conversationService.sendMessage(
                    conversationId, taskerId, text("Ipak bih...")))
                    .isInstanceOf(BusinessRuleException.class);

            // Assert
            assertThat(conversationService.getMessages(conversationId, clientId, PageRequest.of(0, 50)))
                    .hasSize(1);
        }

        @Test
        @DisplayName("Someone outside the conversation can neither read nor write")
        void conversation_isClosedToOutsiders() {
            // Arrange
            UUID conversationId = conversationOf(submitOffer(publishedTask(), taskerId));

            // Act + Assert
            assertThatThrownBy(() -> conversationService.sendMessage(
                    conversationId, otherTaskerId, text("Ja bih jeftinije")))
                    .isInstanceOf(NotResourceOwnerException.class);
            assertThatThrownBy(() -> conversationService.getMessages(
                    conversationId, otherTaskerId, PageRequest.of(0, 50)))
                    .isInstanceOf(NotResourceOwnerException.class);
        }
    }

    @Nested
    class UnreadAndNotifications {

        @Test
        @DisplayName("Unread counts only the other party's messages, per conversation and in total")
        void unread_countsOnlyTheOtherPartysMessages() {
            // Arrange
            UUID conversationId = conversationOf(submitOffer(publishedTask(), taskerId));

            // Act
            conversationService.sendMessage(conversationId, clientId, text("Prva"));
            conversationService.sendMessage(conversationId, clientId, text("Druga"));
            conversationService.sendMessage(conversationId, taskerId, text("Odgovor"));

            // Assert
            assertThat(conversationService.countUnread(taskerId)).isEqualTo(2);
            assertThat(conversationService.countUnread(clientId)).isEqualTo(1);
            assertThat(onlyConversationOf(taskerId).unreadCount()).isEqualTo(2);
        }

        @Test
        @DisplayName("Marking a conversation read clears it for the reader only")
        void markAsRead_clearsUnreadForTheReaderOnly() {
            // Arrange
            UUID conversationId = conversationOf(submitOffer(publishedTask(), taskerId));
            conversationService.sendMessage(conversationId, clientId, text("Pitanje"));
            conversationService.sendMessage(conversationId, taskerId, text("Odgovor"));

            // Act
            int marked = conversationService.markAsRead(conversationId, taskerId);

            // Assert
            assertThat(marked).isEqualTo(1);
            assertThat(conversationService.countUnread(taskerId)).isZero();
            assertThat(conversationService.countUnread(clientId)).isEqualTo(1);
        }

        @Test
        @DisplayName("Many messages produce a single notification until it is read")
        void notifications_areDeduplicated_perConversationUntilRead() {
            // Arrange
            UUID conversationId = conversationOf(submitOffer(publishedTask(), taskerId));

            // Act
            conversationService.sendMessage(conversationId, clientId, text("Jedan"));
            conversationService.sendMessage(conversationId, clientId, text("Dva"));
            conversationService.sendMessage(conversationId, clientId, text("Tri"));

            // Assert
            assertThat(newMessageNotificationsFor(taskerId)).hasSize(1);
        }

        @Test
        @DisplayName("Reading the conversation clears its notification, so the next message notifies again")
        void markAsRead_clearsTheNotification_andTheNextMessageNotifiesAgain() {
            // Arrange
            UUID conversationId = conversationOf(submitOffer(publishedTask(), taskerId));
            conversationService.sendMessage(conversationId, clientId, text("Prva"));

            // Act
            conversationService.markAsRead(conversationId, taskerId);

            // Assert
            assertThat(newMessageNotificationsFor(taskerId))
                    .singleElement()
                    .extracting(Notification::isRead)
                    .isEqualTo(true);

            // Act
            conversationService.sendMessage(conversationId, clientId, text("Druga"));

            // Assert
            assertThat(newMessageNotificationsFor(taskerId))
                    .filteredOn(n -> !n.isRead())
                    .hasSize(1);
        }
    }

    @Nested
    class Listing {

        @Test
        @DisplayName("Conversations are listed by latest activity, silent ones last")
        void getMyConversations_ordersByLatestActivity() {
            // Arrange
            UUID quiet = conversationOf(submitOffer(publishedTask(), taskerId));
            UUID older = conversationOf(submitOffer(publishedTask(), taskerId));
            UUID newer = conversationOf(submitOffer(publishedTask(), taskerId));

            conversationService.sendMessage(older, clientId, text("Starija"));
            conversationService.sendMessage(newer, clientId, text("Novija"));

            // Act
            List<UUID> order = conversationService.getMyConversations(taskerId, PageRequest.of(0, 20))
                    .getContent().stream().map(ConversationResponse::id).toList();

            // Assert
            assertThat(order).containsExactly(newer, older, quiet);
        }
    }

    private UUID publishedTask() {
        UUID taskId = taskService.createTask(clientId, new CreateTaskRequest(
                "Popravka slavine", "Curi ispod sudopera",
                category.getId(), municipality.getId(), new BigDecimal("80.00"))).id();
        taskService.publishTask(taskId, clientId);
        return taskId;
    }

    private UUID submitOffer(UUID taskId, UUID tasker) {
        return offerService.submitOffer(taskId, tasker,
                new CreateOfferRequest(new BigDecimal("75.00"), "Mogu danas")).id();
    }

    private UUID conversationOf(UUID offerId) {
        Offer offer = offerRepository.findById(offerId).orElseThrow();
        return conversationRepository.findByOffer(offer).map(Conversation::getId).orElseThrow();
    }

    private ConversationStatus conversationStatusFor(UUID offerId) {
        Offer offer = offerRepository.findById(offerId).orElseThrow();
        return conversationRepository.findByOffer(offer).map(Conversation::getStatus).orElseThrow();
    }

    private ConversationResponse onlyConversationOf(UUID userId) {
        return conversationService.getMyConversations(userId, PageRequest.of(0, 20))
                .getContent().getFirst();
    }

    private List<Notification> newMessageNotificationsFor(UUID userId) {
        return notificationRepository
                .findByRecipientOrderByCreatedAtDesc(userRepository.findById(userId).orElseThrow())
                .stream()
                .filter(n -> n.getType() == NotificationType.NEW_MESSAGE)
                .toList();
    }

    private SendMessageRequest text(String content) {
        return new SendMessageRequest(content);
    }

    private UUID register(String email) {
        return authService.register(new RegisterRequest(email, "password123", "Test", "User", null)).userId();
    }
}
