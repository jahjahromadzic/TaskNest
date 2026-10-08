package ba.tfb.tasknest.dto.report;

import ba.tfb.tasknest.entity.enums.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReportRequest(

        @NotNull
        ReportReason reason,

        @Size(max = 500)
        String comment
) {
}
