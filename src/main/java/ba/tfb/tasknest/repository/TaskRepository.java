package ba.tfb.tasknest.repository;

import ba.tfb.tasknest.dto.admin.AdminTaskResponse;
import ba.tfb.tasknest.dto.client.ClientHireResponse;
import ba.tfb.tasknest.dto.task.TaskSummaryResponse;
import ba.tfb.tasknest.entity.Task;
import ba.tfb.tasknest.entity.enums.TaskStatus;
import ba.tfb.tasknest.repository.projection.ClientTaskStats;
import ba.tfb.tasknest.repository.projection.TaskStatusCount;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    @Lock(LockModeType.PESSIMISTIC_READ)
    Optional<Task> findWithSharedLockById(UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Task> findWithWriteLockById(UUID id);

    @Query("select t.id from Task t where t.status = :status and t.assignedAt < :cutoff")
    List<UUID> findIdsAssignedBefore(@Param("status") TaskStatus status,
                                     @Param("cutoff") LocalDateTime cutoff);

    @Query("select t.id from Task t where t.status = :status and t.completedAt < :cutoff")
    List<UUID> findIdsCompletedBefore(@Param("status") TaskStatus status,
                                      @Param("cutoff") LocalDateTime cutoff);

    List<Task> findByStatusAndExpiresAtBefore(TaskStatus status, LocalDateTime moment);

    @Query("""
        select new ba.tfb.tasknest.dto.task.TaskSummaryResponse(
            t.id, t.title, t.budget, t.status,
            c.slug, c.name, m.name, t.publishedAt, t.expiresAt,
            (select p.id from TaskPhoto p where p.task = t and p.position = 0))
        from Task t
        join t.category c
        join t.municipality m
        where t.status = :status
          and (t.expiresAt is null or t.expiresAt > :now)
          and (:categoryId is null or c.id = :categoryId)
          and (:region = '' or m.region = :region)
          and (:municipalityId is null or m.id = :municipalityId)
          and (:search = ''
               or cast(function('translate', lower(t.title), 'čćšđžČĆŠĐŽ', 'ccsdzccsdz') as String) like concat('%', :search, '%') escape '!'
               or cast(function('translate', lower(coalesce(t.description, '')), 'čćšđžČĆŠĐŽ', 'ccsdzccsdz') as String)
                  like concat('%', :search, '%') escape '!')
        """)
    Page<TaskSummaryResponse> findOpenTasks(@Param("status") TaskStatus status,
                                            @Param("now") LocalDateTime now,
                                            @Param("categoryId") UUID categoryId,
                                            @Param("region") String region,
                                            @Param("municipalityId") UUID municipalityId,
                                            @Param("search") String search,
                                            Pageable pageable);

    @Query("""
        select new ba.tfb.tasknest.dto.task.TaskSummaryResponse(
            t.id, t.title, t.budget, t.status,
            c.slug, c.name, m.name, t.publishedAt, t.expiresAt,
            (select p.id from TaskPhoto p where p.task = t and p.position = 0))
        from Task t
        join t.category c
        join t.municipality m
        where t.status = :status
          and (t.expiresAt is null or t.expiresAt > :now)
          and t.client.id <> :taskerId
          and c.active = true
          and exists (
              select 1 from TaskerProfile p
              join p.categories pc
              join p.municipalities pm
              where p.user.id = :taskerId
                and pc.id = c.id
                and pm.id = m.id)
        """)
    Page<TaskSummaryResponse> findMatchingTasks(@Param("taskerId") UUID taskerId,
                                                @Param("status") TaskStatus status,
                                                @Param("now") LocalDateTime now,
                                                Pageable pageable);

    @Query("""
        select new ba.tfb.tasknest.dto.task.TaskSummaryResponse(
            t.id, t.title, t.budget, t.status,
            c.slug, c.name, m.name, t.publishedAt, t.expiresAt,
            (select p.id from TaskPhoto p where p.task = t and p.position = 0))
        from Task t
        join t.category c
        join t.municipality m
        where t.client.id = :clientId
          and t.status in :statuses
        """)
    Page<TaskSummaryResponse> findByClientId(@Param("clientId") UUID clientId,
                                             @Param("statuses") Collection<TaskStatus> statuses,
                                             Pageable pageable);

    @Query("""
        select new ba.tfb.tasknest.repository.projection.TaskStatusCount(t.status, count(t))
        from Task t
        where t.client.id = :clientId
        group by t.status
        """)
    List<TaskStatusCount> countByStatusForClient(@Param("clientId") UUID clientId);

    @Query("""
        select new ba.tfb.tasknest.dto.task.TaskSummaryResponse(
            t.id, t.title, t.budget, t.status,
            c.slug, c.name, m.name, t.publishedAt, t.expiresAt,
            (select p.id from TaskPhoto p where p.task = t and p.position = 0))
        from Task t
        join t.category c
        join t.municipality m
        join t.acceptedOffer o
        where o.tasker.id = :taskerId
        """)
    Page<TaskSummaryResponse> findAssignedToTasker(@Param("taskerId") UUID taskerId,
                                                   Pageable pageable);

    long countByStatus(TaskStatus status);

    @Query("""
        select new ba.tfb.tasknest.repository.projection.ClientTaskStats(
            coalesce(sum(case when t.status <> ba.tfb.tasknest.entity.enums.TaskStatus.DRAFT then 1 else 0 end), 0),
            coalesce(sum(case when t.acceptedOffer is not null then 1 else 0 end), 0),
            coalesce(sum(case when t.status = ba.tfb.tasknest.entity.enums.TaskStatus.CLOSED then 1 else 0 end), 0),
            coalesce(sum(case when t.status = ba.tfb.tasknest.entity.enums.TaskStatus.CANCELLED then 1 else 0 end), 0))
        from Task t
        where t.client.id = :clientId
        """)
    ClientTaskStats statsOfClient(@Param("clientId") UUID clientId);

    @Query(value = """
        select new ba.tfb.tasknest.dto.client.ClientHireResponse(
            t.id, t.title, t.status, c.slug, c.name,
            tasker.id, concat(tasker.firstName, ' ', tasker.lastName),
            t.assignedAt, t.completedAt)
        from Task t
        join t.acceptedOffer o
        join o.tasker tasker
        join t.category c
        where t.client.id = :clientId
        order by t.assignedAt desc
        """,
        countQuery = """
        select count(t) from Task t
        where t.client.id = :clientId and t.acceptedOffer is not null
        """)
    Page<ClientHireResponse> findHiresOfClient(@Param("clientId") UUID clientId, Pageable pageable);

    @Query(value = """
        select new ba.tfb.tasknest.dto.admin.AdminTaskResponse(
            t.id, t.title, t.status, c.slug, c.name, m.name,
            cl.id, concat(cl.firstName, ' ', cl.lastName), cl.email,
            t.budget, t.createdAt, t.publishedAt)
        from Task t
        join t.category c
        join t.municipality m
        join t.client cl
        where t.status <> ba.tfb.tasknest.entity.enums.TaskStatus.DRAFT
          and (:status is null or t.status = :status)
          and (lower(t.title) like concat('%', :search, '%')
               or lower(cl.email) like concat('%', :search, '%')
               or lower(concat(cl.firstName, ' ', cl.lastName)) like concat('%', :search, '%'))
        order by t.createdAt desc
        """,
        countQuery = """
        select count(t) from Task t
        join t.client cl
        where t.status <> ba.tfb.tasknest.entity.enums.TaskStatus.DRAFT
          and (:status is null or t.status = :status)
          and (lower(t.title) like concat('%', :search, '%')
               or lower(cl.email) like concat('%', :search, '%')
               or lower(concat(cl.firstName, ' ', cl.lastName)) like concat('%', :search, '%'))
        """)
    Page<AdminTaskResponse> findForAdmin(@Param("status") TaskStatus status,
                                         @Param("search") String search,
                                         Pageable pageable);
}
