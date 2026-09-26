package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.dto.common.PagedResponse;
import ba.tfb.tasknest.dto.task.CreateTaskRequest;
import ba.tfb.tasknest.dto.task.TaskResponse;
import ba.tfb.tasknest.dto.task.TaskSummaryResponse;
import ba.tfb.tasknest.security.UserPrincipal;
import ba.tfb.tasknest.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    @PreAuthorize("hasRole('CLIENT')")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@Valid @RequestBody CreateTaskRequest request,
                               @AuthenticationPrincipal UserPrincipal principal) {
        return taskService.createTask(principal.getId(), request);
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasRole('CLIENT')")
    public TaskResponse publish(@PathVariable UUID id,
                                @AuthenticationPrincipal UserPrincipal principal) {
        return taskService.publishTask(id, principal.getId());
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('CLIENT')")
    public TaskResponse cancel(@PathVariable UUID id,
                               @AuthenticationPrincipal UserPrincipal principal) {
        return taskService.cancelTask(id, principal.getId());
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasRole('TASKER')")
    public TaskResponse start(@PathVariable UUID id,
                              @AuthenticationPrincipal UserPrincipal principal) {
        return taskService.startTask(id, principal.getId());
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize("hasRole('TASKER')")
    public TaskResponse complete(@PathVariable UUID id,
                                 @AuthenticationPrincipal UserPrincipal principal) {
        return taskService.completeTask(id, principal.getId());
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("hasRole('CLIENT')")
    public TaskResponse close(@PathVariable UUID id,
                              @AuthenticationPrincipal UserPrincipal principal) {
        return taskService.closeTask(id, principal.getId());
    }

    @GetMapping
    public PagedResponse<TaskSummaryResponse> browse(
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID municipalityId,
            @PageableDefault(size = 20, sort = "publishedAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return PagedResponse.from(taskService.browseTasks(categoryId, municipalityId, pageable));
    }

    @GetMapping("/matching")
    @PreAuthorize("hasRole('TASKER')")
    public PagedResponse<TaskSummaryResponse> matching(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20, sort = "publishedAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return PagedResponse.from(taskService.getMatchingTasks(principal.getId(), pageable));
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('CLIENT')")
    public PagedResponse<TaskSummaryResponse> mine(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20, sort = "createdAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return PagedResponse.from(taskService.getMyTasks(principal.getId(), pageable));
    }

    @GetMapping("/assigned")
    @PreAuthorize("hasRole('TASKER')")
    public PagedResponse<TaskSummaryResponse> assigned(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20, sort = "publishedAt",
                    direction = Sort.Direction.DESC) Pageable pageable) {
        return PagedResponse.from(taskService.getAssignedTasks(principal.getId(), pageable));
    }

    @GetMapping("/{id}")
    public TaskResponse getOne(@PathVariable UUID id,
                               @AuthenticationPrincipal UserPrincipal principal) {
        UUID viewerId = principal != null ? principal.getId() : null;
        return taskService.getTask(id, viewerId);
    }
}