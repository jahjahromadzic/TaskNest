package ba.tfb.tasknest.dto.admin;

import ba.tfb.tasknest.entity.enums.AccountStatus;
import ba.tfb.tasknest.entity.enums.ReportReason;
import ba.tfb.tasknest.entity.enums.ReportStatus;
import ba.tfb.tasknest.entity.enums.ReportTarget;
import ba.tfb.tasknest.entity.enums.TaskStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminReportResponse(
        UUID id,
        ReportTarget targetType,
        ReportReason reason,
        String comment,
        ReportStatus status,
        LocalDateTime createdAt,
        UUID reporterId,
        String reporterName,
        UUID taskId,
        String taskTitle,
        TaskStatus taskStatus,
        UUID reportedUserId,
        String reportedUserName,
        String reportedUserEmail,
        AccountStatus reportedUserStatus,
        long openReportsOnTarget,
        String resolvedByName,
        LocalDateTime resolvedAt
) {
}
