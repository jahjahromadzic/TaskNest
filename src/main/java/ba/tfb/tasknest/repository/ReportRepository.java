package ba.tfb.tasknest.repository;

import ba.tfb.tasknest.dto.admin.AdminReportResponse;
import ba.tfb.tasknest.entity.Report;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.ReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ReportRepository extends JpaRepository<Report, UUID> {

    boolean existsByReporterIdAndTaskIdAndStatus(UUID reporterId, UUID taskId, ReportStatus status);

    boolean existsByReporterIdAndReportedUserIdAndStatus(UUID reporterId, UUID reportedUserId, ReportStatus status);

    long countByReporterIdAndCreatedAtAfter(UUID reporterId, LocalDateTime since);

    long countByStatus(ReportStatus status);

    @Query(value = """
        select new ba.tfb.tasknest.dto.admin.AdminReportResponse(
            r.id, r.targetType, r.reason, r.comment, r.status, r.createdAt,
            rep.id, concat(rep.firstName, ' ', rep.lastName),
            t.id, t.title, t.status,
            u.id, concat(u.firstName, ' ', u.lastName), u.email, u.accountStatus,
            (select count(o) from Report o
             where o.status = ba.tfb.tasknest.entity.enums.ReportStatus.OPEN
               and (o.task = t or o.reportedUser = u)),
            concat(rb.firstName, ' ', rb.lastName), r.resolvedAt)
        from Report r
        join r.reporter rep
        left join r.task t
        left join r.reportedUser u
        left join r.resolvedBy rb
        where (:status is null or r.status = :status)
        order by r.createdAt desc
        """,
        countQuery = """
        select count(r) from Report r where (:status is null or r.status = :status)
        """)
    Page<AdminReportResponse> findForAdmin(@Param("status") ReportStatus status, Pageable pageable);

    @Modifying
    @Query("""
        update Report r set r.status = ba.tfb.tasknest.entity.enums.ReportStatus.RESOLVED,
            r.resolvedBy = :admin, r.resolvedAt = :now
        where r.status = ba.tfb.tasknest.entity.enums.ReportStatus.OPEN and r.task.id = :taskId
        """)
    int resolveOpenForTask(@Param("taskId") UUID taskId, @Param("admin") User admin, @Param("now") LocalDateTime now);

    @Modifying
    @Query("""
        update Report r set r.status = ba.tfb.tasknest.entity.enums.ReportStatus.RESOLVED,
            r.resolvedBy = :admin, r.resolvedAt = :now
        where r.status = ba.tfb.tasknest.entity.enums.ReportStatus.OPEN and r.reportedUser.id = :userId
        """)
    int resolveOpenForUser(@Param("userId") UUID userId, @Param("admin") User admin, @Param("now") LocalDateTime now);
}
