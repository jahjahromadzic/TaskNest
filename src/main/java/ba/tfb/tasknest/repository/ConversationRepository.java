package ba.tfb.tasknest.repository;

import ba.tfb.tasknest.entity.Conversation;
import ba.tfb.tasknest.entity.Offer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByOffer(Offer offer);

    /** Razgovori u kojima korisnik ucestvuje - kao klijent taska ili kao tasker ponude. */
    @Query("""
            select c from Conversation c
            where c.offer.tasker.id = :userId
               or c.offer.task.client.id = :userId
            """)
    List<Conversation> findAllByParticipant(@Param("userId") UUID userId);
}
