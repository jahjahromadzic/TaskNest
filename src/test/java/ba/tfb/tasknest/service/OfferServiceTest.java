package ba.tfb.tasknest.service;

import ba.tfb.tasknest.domain.InvalidTaskTransitionException;
import ba.tfb.tasknest.dto.offer.CreateOfferRequest;
import ba.tfb.tasknest.dto.offer.OfferResponse;
import ba.tfb.tasknest.entity.Conversation;
import ba.tfb.tasknest.entity.Offer;
import ba.tfb.tasknest.entity.Task;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.ConversationStatus;
import ba.tfb.tasknest.entity.enums.OfferStatus;
import ba.tfb.tasknest.entity.enums.TaskStatus;
import ba.tfb.tasknest.exception.BusinessRuleException;
import ba.tfb.tasknest.exception.NotResourceOwnerException;
import ba.tfb.tasknest.exception.ResourceNotFoundException;
import ba.tfb.tasknest.repository.ConversationRepository;
import ba.tfb.tasknest.repository.OfferRepository;
import ba.tfb.tasknest.repository.TaskRepository;
import ba.tfb.tasknest.repository.UserRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Poslovna pravila OfferService-a, bez Springa i baze.
 * <p>
 * Konkurentnost i optimistic locking pokriva OfferConcurrencyTest - ovdje se
 * testiraju pravila koja ne zavise od baze.
 */
@ExtendWith(MockitoExtension.class)
class OfferServiceTest {

    private static final UUID TASK_ID = UUID.randomUUID();
    private static final UUID OFFER_ID = UUID.randomUUID();
    private static final UUID OTHER_OFFER_ID = UUID.randomUUID();
    private static final UUID CLIENT_ID = UUID.randomUUID();
    private static final UUID TASKER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();

    @Mock private OfferRepository offerRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private UserRepository userRepository;
    @Mock private ConversationRepository conversationRepository;

    @InjectMocks private OfferService offerService;

    @Nested
    class SubmitOffer {

        @Test
        void submitOffer_throwsBusinessRule_whenTaskIsNotPublished() {
            // Arrange
            Task task = aTask(TaskStatus.DRAFT);
            when(taskRepository.findWithSharedLockById(TASK_ID)).thenReturn(Optional.of(task));

            // Act + Assert
            assertThatThrownBy(() -> offerService.submitOffer(TASK_ID, TASKER_ID, aRequest()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("published");
        }

        @Test
        void submitOffer_throwsBusinessRule_whenTaskHasExpired() {
            // Arrange - status je jos PUBLISHED jer scheduler nije stigao
            Task task = aTask(TaskStatus.PUBLISHED);
            task.setExpiresAt(LocalDateTime.now().minusMinutes(1));
            when(taskRepository.findWithSharedLockById(TASK_ID)).thenReturn(Optional.of(task));

            // Act + Assert
            assertThatThrownBy(() -> offerService.submitOffer(TASK_ID, TASKER_ID, aRequest()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("expired");
        }

        @Test
        void submitOffer_throwsBusinessRule_whenUserOffersOnOwnTask() {
            // Arrange - isti korisnik moze imati i CLIENT i TASKER rolu
            Task task = aTask(TaskStatus.PUBLISHED);
            when(taskRepository.findWithSharedLockById(TASK_ID)).thenReturn(Optional.of(task));

            // Act + Assert
            assertThatThrownBy(() -> offerService.submitOffer(TASK_ID, CLIENT_ID, aRequest()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("your own task");
        }

        @Test
        void submitOffer_throwsNotFound_whenTaskerDoesNotExist() {
            // Arrange
            Task task = aTask(TaskStatus.PUBLISHED);
            when(taskRepository.findWithSharedLockById(TASK_ID)).thenReturn(Optional.of(task));
            when(userRepository.findById(TASKER_ID)).thenReturn(Optional.empty());

            // Act + Assert
            assertThatThrownBy(() -> offerService.submitOffer(TASK_ID, TASKER_ID, aRequest()))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User");
        }

        @Test
        void submitOffer_throwsBusinessRule_whenTaskerAlreadySubmittedOffer() {
            // Arrange
            Task task = aTask(TaskStatus.PUBLISHED);
            User tasker = aTasker();
            when(taskRepository.findWithSharedLockById(TASK_ID)).thenReturn(Optional.of(task));
            when(userRepository.findById(TASKER_ID)).thenReturn(Optional.of(tasker));
            when(offerRepository.findByTaskAndTasker(task, tasker))
                    .thenReturn(Optional.of(anOffer(OFFER_ID, task, tasker, OfferStatus.PENDING)));

            // Act + Assert
            assertThatThrownBy(() -> offerService.submitOffer(TASK_ID, TASKER_ID, aRequest()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("already submitted");
        }

        @Test
        void submitOffer_throwsBusinessRule_whenUniqueConstraintFires() {
            // Arrange - paralelan zahtjev je prosao provjeru, baza je odbila drugi upis
            Task task = aTask(TaskStatus.PUBLISHED);
            User tasker = aTasker();
            when(taskRepository.findWithSharedLockById(TASK_ID)).thenReturn(Optional.of(task));
            when(userRepository.findById(TASKER_ID)).thenReturn(Optional.of(tasker));
            when(offerRepository.findByTaskAndTasker(task, tasker)).thenReturn(Optional.empty());
            when(offerRepository.saveAndFlush(any(Offer.class)))
                    .thenThrow(new DataIntegrityViolationException("uq_offers_task_tasker"));

            // Act + Assert - tehnicka greska se prevodi u poslovnu, ne izlazi kao 500
            assertThatThrownBy(() -> offerService.submitOffer(TASK_ID, TASKER_ID, aRequest()))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("already submitted");
        }

        @Test
        void submitOffer_createsPendingOffer_whenTaskAcceptsOffers() {
            // Arrange
            Task task = aTask(TaskStatus.PUBLISHED);
            User tasker = aTasker();
            when(taskRepository.findWithSharedLockById(TASK_ID)).thenReturn(Optional.of(task));
            when(userRepository.findById(TASKER_ID)).thenReturn(Optional.of(tasker));
            when(offerRepository.findByTaskAndTasker(task, tasker)).thenReturn(Optional.empty());
            when(offerRepository.saveAndFlush(any(Offer.class))).thenAnswer(call -> call.getArgument(0));

            // Act
            OfferResponse response = offerService.submitOffer(TASK_ID, TASKER_ID, aRequest());

            // Assert
            assertThat(response.status()).isEqualTo(OfferStatus.PENDING);
            assertThat(response.price()).isEqualByComparingTo("45.00");
            assertThat(response.taskerId()).isEqualTo(TASKER_ID);
            assertThat(response.taskId()).isEqualTo(TASK_ID);
        }
    }

    @Nested
    class AcceptOffer {

        @Test
        void acceptOffer_throwsNotResourceOwner_whenTaskBelongsToAnotherClient() {
            // Arrange
            Task task = aTask(TaskStatus.PUBLISHED);
            Offer offer = anOffer(OFFER_ID, task, aTasker(), OfferStatus.PENDING);
            when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(offer));

            // Act + Assert
            assertThatThrownBy(() -> offerService.acceptOffer(OFFER_ID, OTHER_USER_ID))
                    .isInstanceOf(NotResourceOwnerException.class);

            assertThat(offer.getStatus())
                    .as("odbijen zahtjev ne smije promijeniti stanje ponude")
                    .isEqualTo(OfferStatus.PENDING);
        }

        @Test
        void acceptOffer_throwsBusinessRule_whenOfferIsNotPending() {
            // Arrange
            Task task = aTask(TaskStatus.PUBLISHED);
            Offer offer = anOffer(OFFER_ID, task, aTasker(), OfferStatus.WITHDRAWN);
            when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(offer));

            // Act + Assert
            assertThatThrownBy(() -> offerService.acceptOffer(OFFER_ID, CLIENT_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("pending");
        }

        @Test
        void acceptOffer_throwsInvalidTransition_whenTaskIsNotPublished() {
            // Arrange - DRAFT -> ASSIGNED nije dozvoljen prelaz
            Task task = aTask(TaskStatus.DRAFT);
            Offer offer = anOffer(OFFER_ID, task, aTasker(), OfferStatus.PENDING);
            when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(offer));

            // Act + Assert
            assertThatThrownBy(() -> offerService.acceptOffer(OFFER_ID, CLIENT_ID))
                    .isInstanceOf(InvalidTaskTransitionException.class);

            assertThat(task.getStatus()).isEqualTo(TaskStatus.DRAFT);
        }

        @Test
        void acceptOffer_marksOfferAcceptedAndTaskAssigned_whenOfferIsPending() {
            // Arrange
            Task task = aTask(TaskStatus.PUBLISHED);
            Offer offer = anOffer(OFFER_ID, task, aTasker(), OfferStatus.PENDING);
            when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(offer));
            when(offerRepository.findByTaskAndStatus(task, OfferStatus.PENDING))
                    .thenReturn(List.of(offer));

            // Act
            OfferResponse response = offerService.acceptOffer(OFFER_ID, CLIENT_ID);

            // Assert
            assertThat(response.status()).isEqualTo(OfferStatus.ACCEPTED);
            assertThat(task.getStatus()).isEqualTo(TaskStatus.ASSIGNED);
            assertThat(task.getAcceptedOffer()).isSameAs(offer);
        }

        @Test
        void acceptOffer_rejectsOtherPendingOffersAndArchivesTheirConversations() {
            // Arrange
            Task task = aTask(TaskStatus.PUBLISHED);
            Offer accepted = anOffer(OFFER_ID, task, aTasker(), OfferStatus.PENDING);
            Offer rival = anOffer(OTHER_OFFER_ID, task, anotherTasker(), OfferStatus.PENDING);
            Conversation rivalConversation = aConversation();

            when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(accepted));
            when(offerRepository.findByTaskAndStatus(task, OfferStatus.PENDING))
                    .thenReturn(List.of(accepted, rival));
            when(conversationRepository.findByOffer(rival)).thenReturn(Optional.of(rivalConversation));

            // Act
            offerService.acceptOffer(OFFER_ID, CLIENT_ID);

            // Assert
            assertThat(rival.getStatus()).isEqualTo(OfferStatus.REJECTED);
            assertThat(rivalConversation.getStatus()).isEqualTo(ConversationStatus.ARCHIVED);
            assertThat(accepted.getStatus())
                    .as("prihvacena ponuda ne smije biti odbijena zajedno s ostalima")
                    .isEqualTo(OfferStatus.ACCEPTED);
        }
    }

    @Nested
    class WithdrawOffer {

        @Test
        void withdrawOffer_throwsNotResourceOwner_whenOfferBelongsToAnotherTasker() {
            // Arrange
            Offer offer = anOffer(OFFER_ID, aTask(TaskStatus.PUBLISHED), aTasker(), OfferStatus.PENDING);
            when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(offer));

            // Act + Assert
            assertThatThrownBy(() -> offerService.withdrawOffer(OFFER_ID, OTHER_USER_ID))
                    .isInstanceOf(NotResourceOwnerException.class);

            assertThat(offer.getStatus()).isEqualTo(OfferStatus.PENDING);
        }

        @Test
        void withdrawOffer_throwsBusinessRule_whenOfferIsNotPending() {
            // Arrange - prihvacena ponuda se ne povlaci
            Offer offer = anOffer(OFFER_ID, aTask(TaskStatus.ASSIGNED), aTasker(), OfferStatus.ACCEPTED);
            when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(offer));

            // Act + Assert
            assertThatThrownBy(() -> offerService.withdrawOffer(OFFER_ID, TASKER_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("pending");
        }

        @Test
        void withdrawOffer_archivesConversation_whenOfferIsPending() {
            // Arrange
            Offer offer = anOffer(OFFER_ID, aTask(TaskStatus.PUBLISHED), aTasker(), OfferStatus.PENDING);
            Conversation conversation = aConversation();
            when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(offer));
            when(conversationRepository.findByOffer(offer)).thenReturn(Optional.of(conversation));

            // Act
            OfferResponse response = offerService.withdrawOffer(OFFER_ID, TASKER_ID);

            // Assert
            assertThat(response.status()).isEqualTo(OfferStatus.WITHDRAWN);
            assertThat(conversation.getStatus()).isEqualTo(ConversationStatus.ARCHIVED);
        }

        @Test
        void withdrawOffer_succeeds_whenOfferHasNoConversation() {
            // Arrange - razgovor ne postoji dok se ne posalje prva poruka
            Offer offer = anOffer(OFFER_ID, aTask(TaskStatus.PUBLISHED), aTasker(), OfferStatus.PENDING);
            when(offerRepository.findById(OFFER_ID)).thenReturn(Optional.of(offer));
            when(conversationRepository.findByOffer(offer)).thenReturn(Optional.empty());

            // Act
            OfferResponse response = offerService.withdrawOffer(OFFER_ID, TASKER_ID);

            // Assert
            assertThat(response.status()).isEqualTo(OfferStatus.WITHDRAWN);
        }
    }

    @Nested
    class GetOffersForTask {

        @Test
        void getOffersForTask_throwsNotResourceOwner_whenTaskBelongsToAnotherClient() {
            // Arrange
            Task task = aTask(TaskStatus.PUBLISHED);
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.of(task));

            // Act + Assert
            assertThatThrownBy(() -> offerService.getOffersForTask(TASK_ID, OTHER_USER_ID))
                    .isInstanceOf(NotResourceOwnerException.class);
        }

        @Test
        void getOffersForTask_throwsNotFound_whenTaskDoesNotExist() {
            // Arrange
            when(taskRepository.findById(TASK_ID)).thenReturn(Optional.empty());

            // Act + Assert
            assertThatThrownBy(() -> offerService.getOffersForTask(TASK_ID, CLIENT_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Task");
        }
    }

    /**
     * Ovo je logika koju TaskService.cancelTask delegira ovamo, pa se pravilo
     * testira tamo gdje stvarno zivi.
     */
    @Nested
    class RejectActiveOffers {

        @Test
        void rejectActiveOffers_rejectsPendingAndAcceptedOffers_andArchivesConversations() {
            // Arrange
            Task task = aTask(TaskStatus.ASSIGNED);
            Offer pending = anOffer(OFFER_ID, task, aTasker(), OfferStatus.PENDING);
            Offer accepted = anOffer(OTHER_OFFER_ID, task, anotherTasker(), OfferStatus.ACCEPTED);
            Conversation pendingConversation = aConversation();
            Conversation acceptedConversation = aConversation();

            when(offerRepository.findByTask(task)).thenReturn(List.of(pending, accepted));
            when(conversationRepository.findByOffer(pending)).thenReturn(Optional.of(pendingConversation));
            when(conversationRepository.findByOffer(accepted)).thenReturn(Optional.of(acceptedConversation));

            // Act
            offerService.rejectActiveOffers(task);

            // Assert
            assertThat(pending.getStatus()).isEqualTo(OfferStatus.REJECTED);
            assertThat(accepted.getStatus()).isEqualTo(OfferStatus.REJECTED);
            assertThat(pendingConversation.getStatus()).isEqualTo(ConversationStatus.ARCHIVED);
            assertThat(acceptedConversation.getStatus()).isEqualTo(ConversationStatus.ARCHIVED);
        }

        @Test
        void rejectActiveOffers_leavesTerminalOffersUntouched() {
            // Arrange
            Task task = aTask(TaskStatus.PUBLISHED);
            Offer withdrawn = anOffer(OFFER_ID, task, aTasker(), OfferStatus.WITHDRAWN);
            Offer alreadyRejected = anOffer(OTHER_OFFER_ID, task, anotherTasker(), OfferStatus.REJECTED);

            when(offerRepository.findByTask(task)).thenReturn(List.of(withdrawn, alreadyRejected));

            // Act
            offerService.rejectActiveOffers(task);

            // Assert - povucena ponuda ostaje povucena, ne postaje odbijena
            assertThat(withdrawn.getStatus()).isEqualTo(OfferStatus.WITHDRAWN);
            assertThat(alreadyRejected.getStatus()).isEqualTo(OfferStatus.REJECTED);
        }
    }

    // ---------- fixtures ----------

    private CreateOfferRequest aRequest() {
        return new CreateOfferRequest(new BigDecimal("45.00"), "Mogu danas");
    }

    private Task aTask(TaskStatus status) {
        User client = new User();
        client.setId(CLIENT_ID);
        client.setFirstName("Amra");
        client.setLastName("Client");

        Task task = new Task();
        task.setId(TASK_ID);
        task.setClient(client);
        task.setTitle("Popravka slavine");
        task.setStatus(status);
        if (status != TaskStatus.DRAFT) {
            task.setExpiresAt(LocalDateTime.now().plusDays(29));
        }
        return task;
    }

    private User aTasker() {
        User tasker = new User();
        tasker.setId(TASKER_ID);
        tasker.setFirstName("Mirza");
        tasker.setLastName("Tasker");
        return tasker;
    }

    private User anotherTasker() {
        User tasker = new User();
        tasker.setId(UUID.randomUUID());
        tasker.setFirstName("Selma");
        tasker.setLastName("Rival");
        return tasker;
    }

    private Offer anOffer(UUID id, Task task, User tasker, OfferStatus status) {
        Offer offer = new Offer();
        offer.setId(id);
        offer.setTask(task);
        offer.setTasker(tasker);
        offer.setPrice(new BigDecimal("45.00"));
        offer.setStatus(status);
        return offer;
    }

    private Conversation aConversation() {
        Conversation conversation = new Conversation();
        conversation.setId(UUID.randomUUID());
        conversation.setStatus(ConversationStatus.OPEN);
        return conversation;
    }
}
