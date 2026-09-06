package ba.tfb.tasknest.service;

import ba.tfb.tasknest.domain.TaskStateMachine;
import ba.tfb.tasknest.dto.offer.CreateOfferRequest;
import ba.tfb.tasknest.dto.offer.OfferResponse;
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
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OfferService {

    /** Ponude koje jos "zive" - one koje otkazivanje taska mora povuci sa sobom. */
    private static final Set<OfferStatus> ACTIVE_STATUSES =
            EnumSet.of(OfferStatus.PENDING, OfferStatus.ACCEPTED);

    private final OfferRepository offerRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;


    @Transactional
    public OfferResponse submitOffer(UUID taskId, UUID taskerId, CreateOfferRequest request) {
        // OPTIMISTIC lock, a ne obican findById: odluka se donosi na osnovu stanja
        // taska, ali se upisuje u offers, pa task ostaje neizmijenjen i njegova
        // verzija se inace ne bi provjeravala. Bez ovoga paralelni acceptOffer moze
        // prebaciti task u ASSIGNED izmedju ove provjere i naseg commita, i ponuda
        // bi ostala viseci u PENDING na dodijeljenom tasku. Ovako druga transakcija
        // dobije OptimisticLockException.
        Task task = taskRepository.findWithOptimisticLockById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        if (task.getStatus() != TaskStatus.PUBLISHED) {
            throw new BusinessRuleException("Offers can only be submitted on published tasks");
        }

        // Task ostaje PUBLISHED dok ga scheduler ne prebaci u EXPIRED, pa se istek
        // mora provjeriti i ovdje - taj prozor postoji bez obzira na scheduler.
        if (task.getExpiresAt() != null && task.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessRuleException("This task has expired and no longer accepts offers");
        }

        // A user may hold both CLIENT and TASKER roles, so this case is real
        if (task.getClient().getId().equals(taskerId)) {
            throw new BusinessRuleException("You cannot submit an offer on your own task");
        }

        User tasker = userRepository.findById(taskerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", taskerId));

        if (offerRepository.findByTaskAndTasker(task, tasker).isPresent()) {
            throw new BusinessRuleException("You have already submitted an offer on this task");
        }

        Offer offer = new Offer();
        offer.setTask(task);
        offer.setTasker(tasker);
        offer.setPrice(request.price());
        offer.setMessage(request.message());
        offer.setStatus(OfferStatus.PENDING);

        try {
            // saveAndFlush, ne save: bez flusha bi uq_offers_task_tasker pukao tek na
            // commitu, izvan ovog catch-a. Provjera iznad je check-then-act i ne stiti
            // od paralelnih zahtjeva - constraint je stvarna zastita, ovo je prevod.
            return OfferResponse.from(offerRepository.saveAndFlush(offer));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessRuleException("You have already submitted an offer on this task");
        }
    }

    @Transactional
    public OfferResponse acceptOffer(UUID offerId, UUID clientId) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("Offer", offerId));

        Task task = offer.getTask();

        if (!task.getClient().getId().equals(clientId)) {
            throw new NotResourceOwnerException("Task does not belong to this user");
        }

        if (offer.getStatus() != OfferStatus.PENDING) {
            throw new BusinessRuleException("Only pending offers can be accepted");
        }

        TaskStateMachine.validateTransition(task.getStatus(), TaskStatus.ASSIGNED);

        offer.setStatus(OfferStatus.ACCEPTED);
        task.setStatus(TaskStatus.ASSIGNED);
        task.setAcceptedOffer(offer);

        rejectRemainingOffers(task, offer.getId());

        return OfferResponse.from(offer);
    }

    /**
     * A tasker withdraws their own offer. Allowed only while still pending.
     */
    @Transactional
    public OfferResponse withdrawOffer(UUID offerId, UUID taskerId) {
        Offer offer = offerRepository.findById(offerId)
                .orElseThrow(() -> new ResourceNotFoundException("Offer", offerId));

        if (!offer.getTasker().getId().equals(taskerId)) {
            throw new NotResourceOwnerException("Offer does not belong to this user");
        }

        if (offer.getStatus() != OfferStatus.PENDING) {
            throw new BusinessRuleException("Only pending offers can be withdrawn");
        }

        offer.setStatus(OfferStatus.WITHDRAWN);
        archiveConversation(offer);

        return OfferResponse.from(offer);
    }

    /**
     * The client lists all offers received on their own task.
     */
    @Transactional(readOnly = true)
    public List<OfferResponse> getOffersForTask(UUID taskId, UUID clientId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        if (!task.getClient().getId().equals(clientId)) {
            throw new NotResourceOwnerException("Task does not belong to this user");
        }

        return offerRepository.findByTask(task).stream()
                .map(OfferResponse::from)
                .toList();
    }

    /**
     * The tasker lists their own offers.
     */
    @Transactional(readOnly = true)
    public List<OfferResponse> getMyOffers(UUID taskerId) {
        User tasker = userRepository.findById(taskerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", taskerId));

        return offerRepository.findByTasker(tasker).stream()
                .map(OfferResponse::from)
                .toList();
    }

    /**
     * Odbija sve jos zive ponude taska (PENDING i ACCEPTED) i arhivira im razgovore.
     * Zove se pri otkazivanju taska. Stoji ovdje, a ne u TaskService-u, jer je
     * zivotni ciklus ponude i razgovora vlasnistvo ovog servisa - inace bi se
     * arhiviranje duplo pisalo na dva mjesta.
     */
    @Transactional
    public void rejectActiveOffers(Task task) {
        for (Offer offer : offerRepository.findByTask(task)) {
            if (!ACTIVE_STATUSES.contains(offer.getStatus())) {
                continue;
            }
            offer.setStatus(OfferStatus.REJECTED);
            archiveConversation(offer);
        }
    }

    private void rejectRemainingOffers(Task task, UUID acceptedOfferId) {
        List<Offer> pending =
                offerRepository.findByTaskAndStatus(task, OfferStatus.PENDING);

        for (Offer other : pending) {
            if (other.getId().equals(acceptedOfferId)) {
                continue;
            }
            other.setStatus(OfferStatus.REJECTED);
            archiveConversation(other);
        }
    }

    private void archiveConversation(Offer offer) {
        conversationRepository.findByOffer(offer)
                .ifPresent(conversation ->
                        conversation.setStatus(ConversationStatus.ARCHIVED));
    }
}