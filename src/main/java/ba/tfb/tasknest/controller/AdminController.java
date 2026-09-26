package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.dto.admin.AdminUserResponse;
import ba.tfb.tasknest.dto.admin.RemoveTaskRequest;
import ba.tfb.tasknest.dto.common.PagedResponse;
import ba.tfb.tasknest.dto.task.TaskResponse;
import ba.tfb.tasknest.dto.taskerprofile.TaskerProfileResponse;
import ba.tfb.tasknest.entity.enums.AccountStatus;
import ba.tfb.tasknest.security.UserPrincipal;
import ba.tfb.tasknest.service.AdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/users")
    public PagedResponse<AdminUserResponse> users(
            @RequestParam(required = false) AccountStatus status,
            @RequestParam(required = false) String email,
            @PageableDefault(size = 20) Pageable pageable) {
        return PagedResponse.from(adminService.listUsers(status, email,
                PageRequest.of(pageable.getPageNumber(), pageable.getPageSize())));
    }

    @PostMapping("/users/{id}/suspend")
    public AdminUserResponse suspend(@PathVariable UUID id,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        return adminService.suspendUser(principal.getId(), id);
    }

    @PostMapping("/users/{id}/reactivate")
    public AdminUserResponse reactivate(@PathVariable UUID id,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        return adminService.reactivateUser(principal.getId(), id);
    }

    @PostMapping("/tasker-profiles/{id}/verify")
    public TaskerProfileResponse verify(@PathVariable UUID id,
                                        @AuthenticationPrincipal UserPrincipal principal) {
        return adminService.setTaskerVerified(principal.getId(), id, true);
    }

    @PostMapping("/tasker-profiles/{id}/unverify")
    public TaskerProfileResponse unverify(@PathVariable UUID id,
                                          @AuthenticationPrincipal UserPrincipal principal) {
        return adminService.setTaskerVerified(principal.getId(), id, false);
    }

    @PostMapping("/tasks/{id}/remove")
    public TaskResponse removeTask(@PathVariable UUID id,
                                   @Valid @RequestBody RemoveTaskRequest request,
                                   @AuthenticationPrincipal UserPrincipal principal) {
        return adminService.removeTask(principal.getId(), id, request.reason());
    }
}
