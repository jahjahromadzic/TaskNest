package ba.tfb.tasknest.scheduler;

import ba.tfb.tasknest.service.TaskService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.function.BiPredicate;

@Component
@ConditionalOnProperty(name = "app.task-deadlines.enabled", havingValue = "true", matchIfMissing = true)
@Slf4j
public class TaskDeadlineSchedule {

    private final TaskService taskService;
    private final Clock clock;
    private final int assignmentStartDays;
    private final int completionCloseDays;

    public TaskDeadlineSchedule(TaskService taskService,
                                Clock clock,
                                @Value("${app.task-deadlines.assignment-start-days:14}") int assignmentStartDays,
                                @Value("${app.task-deadlines.completion-close-days:7}") int completionCloseDays) {
        this.taskService = taskService;
        this.clock = clock;
        this.assignmentStartDays = assignmentStartDays;
        this.completionCloseDays = completionCloseDays;
    }

    @Scheduled(
            fixedDelayString = "${app.task-deadlines.interval-ms:60000}",
            initialDelayString = "${app.task-deadlines.initial-delay-ms:15000}")
    public void enforceDeadlines() {
        LocalDateTime now = LocalDateTime.now(clock);

        LocalDateTime assignedCutoff = now.minusDays(assignmentStartDays);
        int reopened = process(taskService.findTasksAssignedBefore(assignedCutoff), assignedCutoff,
                taskService::releaseStaleAssignment, "reopen");

        LocalDateTime completedCutoff = now.minusDays(completionCloseDays);
        int closed = process(taskService.findTasksCompletedBefore(completedCutoff), completedCutoff,
                taskService::autoCloseTask, "close");

        if (reopened > 0 || closed > 0) {
            log.info("Deadlines enforced: {} tasks reopened, {} tasks closed", reopened, closed);
        }
    }

    private int process(Iterable<UUID> taskIds,
                        LocalDateTime cutoff,
                        BiPredicate<UUID, LocalDateTime> action,
                        String name) {
        int done = 0;
        for (UUID taskId : taskIds) {
            try {
                if (action.test(taskId, cutoff)) {
                    done++;
                }
            } catch (RuntimeException e) {
                log.warn("Could not {} task {}: {}", name, taskId, e.getMessage());
            }
        }
        return done;
    }
}
