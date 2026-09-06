package ba.tfb.tasknest.repository;

import ba.tfb.tasknest.entity.Conversation;
import ba.tfb.tasknest.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByConversationOrderByCreatedAtAsc(Conversation conversation);

    Page<Message> findByConversationOrderByCreatedAtAsc(Conversation conversation, Pageable pageable);

    /** Neprocitane poruke koje su korisniku stigle (tudji sender) u njegovim razgovorima. */
    @Query("""
            select count(m) from Message m
            where m.readAt is null
              and m.sender.id <> :userId
              and (m.conversation.offer.tasker.id = :userId
                   or m.conversation.offer.task.client.id = :userId)
            """)
    long countUnreadForUser(@Param("userId") UUID userId);
}
