package ba.tfb.tasknest.repository;

import ba.tfb.tasknest.entity.Conversation;
import ba.tfb.tasknest.entity.Offer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    Optional<Conversation> findByOffer(Offer offer);

    @Query(value = """
            select c from Conversation c
            join fetch c.offer o
            join fetch o.task t
            join fetch t.client
            join fetch o.tasker
            where o.tasker.id = :userId
               or t.client.id = :userId
            order by c.lastMessageAt desc nulls last, c.createdAt desc
            """,
            countQuery = """
            select count(c) from Conversation c
            where c.offer.tasker.id = :userId
               or c.offer.task.client.id = :userId
            """)
    Page<Conversation> findAllByParticipant(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
            select c from Conversation c
            join fetch c.offer o
            join fetch o.task t
            join fetch t.client
            join fetch o.tasker
            where c.id = :id
            """)
    Optional<Conversation> findWithParticipantsById(@Param("id") UUID id);
}
