package ba.tfb.tasknest.dto.report;

import ba.tfb.tasknest.entity.Report;
import ba.tfb.tasknest.entity.enums.ReportReason;
import ba.tfb.tasknest.entity.enums.ReportStatus;
import ba.tfb.tasknest.entity.enums.ReportTarget;

import java.time.LocalDateTime;
import java.util.UUID;

public record ReportResponse(
        UUID id,
        ReportTarget targetType,
        ReportReason reason,
        ReportStatus status,
        LocalDateTime createdAt
) {

    public static ReportResponse from(Report report) {
        return new ReportResponse(report.getId(), report.getTargetType(), report.getReason(),
                report.getStatus(), report.getCreatedAt());
    }
}
