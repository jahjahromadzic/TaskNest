package ba.tfb.tasknest.service;

import ba.tfb.tasknest.dto.admin.AdminReportResponse;
import ba.tfb.tasknest.dto.report.CreateReportRequest;
import ba.tfb.tasknest.dto.report.ReportResponse;
import ba.tfb.tasknest.entity.Report;
import ba.tfb.tasknest.entity.Task;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.AccountStatus;
import ba.tfb.tasknest.entity.enums.ReportReason;
import ba.tfb.tasknest.entity.enums.ReportStatus;
import ba.tfb.tasknest.entity.enums.ReportTarget;
import ba.tfb.tasknest.entity.enums.TaskStatus;
import ba.tfb.tasknest.exception.BusinessRuleException;
import ba.tfb.tasknest.exception.ResourceNotFoundException;
import ba.tfb.tasknest.repository.ReportRepository;
import ba.tfb.tasknest.repository.TaskRepository;
import ba.tfb.tasknest.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportService {

    public static final int MAX_REPORTS_PER_DAY = 10;

    private static final String ALREADY_REPORTED = "You have already reported this. An administrator will review it.";

    private final ReportRepository reportRepository;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional
    public ReportResponse reportTask(UUID reporterId, UUID taskId, CreateReportRequest request) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));

        if (task.getStatus() == TaskStatus.DRAFT) {
            throw new ResourceNotFoundException("Task", taskId);
        }
        if (task.getClient().getId().equals(reporterId)) {
            throw new BusinessRuleException("You cannot report your own task");
        }
        if (task.getStatus() == TaskStatus.REMOVED) {
            throw new BusinessRuleException("This task has already been removed by a moderator");
        }
        if (reportRepository.existsByReporterIdAndTaskIdAndStatus(reporterId, taskId, ReportStatus.OPEN)) {
            throw new BusinessRuleException(ALREADY_REPORTED);
        }

        Report report = newReport(reporterId, ReportTarget.TASK, request);
        report.setTask(task);
        return save(report);
    }

    @Transactional
    public ReportResponse reportUser(UUID reporterId, UUID userId, CreateReportRequest request) {
        if (userId.equals(reporterId)) {
            throw new BusinessRuleException("You cannot report yourself");
        }

        User reported = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (reported.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new BusinessRuleException("This account is already suspended");
        }
        if (reportRepository.existsByReporterIdAndReportedUserIdAndStatus(reporterId, userId, ReportStatus.OPEN)) {
            throw new BusinessRuleException(ALREADY_REPORTED);
        }

        Report report = newReport(reporterId, ReportTarget.USER, request);
        report.setReportedUser(reported);
        return save(report);
    }

    @Transactional(readOnly = true)
    public Page<AdminReportResponse> listForAdmin(ReportStatus status, Pageable pageable) {
        return reportRepository.findForAdmin(status, pageable);
    }

    @Transactional(readOnly = true)
    public long countOpen() {
        return reportRepository.countByStatus(ReportStatus.OPEN);
    }

    @Transactional
    public void dismiss(UUID adminId, UUID reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report", reportId));

        if (report.getStatus() != ReportStatus.OPEN) {
            throw new BusinessRuleException("This report has already been handled");
        }

        report.setStatus(ReportStatus.DISMISSED);
        report.setResolvedBy(userRepository.getReferenceById(adminId));
        report.setResolvedAt(LocalDateTime.now(clock));
        log.info("Admin {} dismissed report {}", adminId, reportId);
    }

    @Transactional
    public int resolveForTask(UUID adminId, UUID taskId) {
        return reportRepository.resolveOpenForTask(taskId, userRepository.getReferenceById(adminId), LocalDateTime.now(clock));
    }

    @Transactional
    public int resolveForUser(UUID adminId, UUID userId) {
        return reportRepository.resolveOpenForUser(userId, userRepository.getReferenceById(adminId), LocalDateTime.now(clock));
    }

    private Report newReport(UUID reporterId, ReportTarget target, CreateReportRequest request) {
        String comment = request.comment() == null || request.comment().isBlank() ? null : request.comment().strip();

        if (request.reason() == ReportReason.OTHER && comment == null) {
            throw new BusinessRuleException("Describe the problem when the reason is Other");
        }

        LocalDateTime dayAgo = LocalDateTime.now(clock).minusDays(1);
        if (reportRepository.countByReporterIdAndCreatedAtAfter(reporterId, dayAgo) >= MAX_REPORTS_PER_DAY) {
            throw new BusinessRuleException("You have sent too many reports today. Try again tomorrow.");
        }

        Report report = new Report();
        report.setReporter(userRepository.getReferenceById(reporterId));
        report.setTargetType(target);
        report.setReason(request.reason());
        report.setComment(comment);
        return report;
    }

    private ReportResponse save(Report report) {
        try {
            return ReportResponse.from(reportRepository.saveAndFlush(report));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessRuleException(ALREADY_REPORTED);
        }
    }
}
