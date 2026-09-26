package ba.tfb.tasknest.messaging;

import ba.tfb.tasknest.repository.projection.TaskerNotificationTarget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class NewTaskMailer {

    private final JavaMailSender mailSender;
    private final String from;

    public NewTaskMailer(JavaMailSender mailSender,
                         @Value("${app.mail.from}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void sendNewTaskEmails(List<TaskerNotificationTarget> targets, TaskPublishedEvent event) {
        for (TaskerNotificationTarget target : targets) {
            try {
                mailSender.send(buildMessage(target, event));
            } catch (Exception e) {
                log.warn("Sending mail to {} failed for task {}: {}",
                        target.email(), event.taskId(), e.getMessage());
            }
        }
    }

    private SimpleMailMessage buildMessage(TaskerNotificationTarget target, TaskPublishedEvent event) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(target.email());
        message.setSubject("New task in your area: " + event.title());
        message.setText("""
                Hi %s,

                A new task matching your categories and municipalities has been posted:

                  %s

                Open TaskNest to see the details and submit an offer.
                """.formatted(target.fullName(), event.title()));

        return message;
    }
}
