package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.dto.report.CreateReportRequest;
import ba.tfb.tasknest.dto.report.ReportResponse;
import ba.tfb.tasknest.security.UserPrincipal;
import ba.tfb.tasknest.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/tasks/{taskId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse reportTask(@PathVariable UUID taskId,
                                     @Valid @RequestBody CreateReportRequest request,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        return reportService.reportTask(principal.getId(), taskId, request);
    }

    @PostMapping("/users/{userId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ReportResponse reportUser(@PathVariable UUID userId,
                                     @Valid @RequestBody CreateReportRequest request,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        return reportService.reportUser(principal.getId(), userId, request);
    }
}
