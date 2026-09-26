package ba.tfb.tasknest.repository;

import ba.tfb.tasknest.entity.Conversation;
import ba.tfb.tasknest.entity.Message;
import ba.tfb.tasknest.repository.projection.ConversationUnreadCount;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, UUID> {

    List<Message> findByConversationOrderByCreatedAtAsc(Conversation conversation);

    Page<Message> findByConversationOrderByCreatedAtAsc(Conversation conversation, Pageable pageable);

    @Query("""
            select count(m) from Message m
            where m.readAt is null
              and m.sender.id <> :userId
              and (m.conversation.offer.tasker.id = :userId
                   or m.conversation.offer.task.client.id = :userId)
            """)
    long countUnreadForUser(@Param("userId") UUID userId);

    @Query("""
            select new ba.tfb.tasknest.repository.projection.ConversationUnreadCount(
                    m.conversation.id, count(m))
            from Message m
            where m.readAt is null
              and m.sender.id <> :userId
              and m.conversation.id in :conversationIds
            group by m.conversation.id
            """)
    List<ConversationUnreadCount> countUnreadByConversation(
            @Param("userId") UUID userId,
            @Param("conversationIds") Collection<UUID> conversationIds);

    @Modifying
    @Query("""
            update Message m set m.readAt = :now
            where m.conversation = :conversation
              and m.sender.id <> :readerId
              and m.readAt is null
            """)
    int markReadByRecipient(@Param("conversation") Conversation conversation,
                            @Param("readerId") UUID readerId,
                            @Param("now") LocalDateTime now);
}
