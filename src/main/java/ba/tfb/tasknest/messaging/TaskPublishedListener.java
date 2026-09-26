package ba.tfb.tasknest.messaging;

import ba.tfb.tasknest.config.RabbitConfig;
import ba.tfb.tasknest.repository.projection.TaskerNotificationTarget;
import ba.tfb.tasknest.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TaskPublishedListener {

    private final NotificationService notificationService;
    private final NewTaskMailer mailer;

    @RabbitListener(queues = RabbitConfig.TASK_PUBLISHED_QUEUE)
    public void onTaskPublished(TaskPublishedEvent event) {
        log.debug("Received TaskPublishedEvent for task {}", event.taskId());

        List<TaskerNotificationTarget> targets =
                notificationService.notifyTaskersAboutNewTask(event);

        mailer.sendNewTaskEmails(targets, event);
    }
}
