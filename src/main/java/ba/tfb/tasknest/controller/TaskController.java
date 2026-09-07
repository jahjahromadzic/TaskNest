package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.dto.task.CreateTaskRequest;
import ba.tfb.tasknest.dto.task.TaskResponse;
import ba.tfb.tasknest.security.UserPrincipal;
import ba.tfb.tasknest.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(@Valid @RequestBody CreateTaskRequest request,
                               @AuthenticationPrincipal UserPrincipal principal) {
        return taskService.createTask(principal.getId(), request);
    }

    @PostMapping("/{id}/publish")
    public TaskResponse publish(@PathVariable UUID id,
                                @AuthenticationPrincipal UserPrincipal principal) {
        return taskService.publishTask(id, principal.getId());
    }

    @PostMapping("/{id}/cancel")
    public TaskResponse cancel(@PathVariable UUID id,
                               @AuthenticationPrincipal UserPrincipal principal) {
        return taskService.cancelTask(id, principal.getId());
    }

    @GetMapping("/{id}")
    public TaskResponse getOne(@PathVariable UUID id) {
        return taskService.getTask(id);
    }
}