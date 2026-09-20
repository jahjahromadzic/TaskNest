package ba.tfb.tasknest.messaging;

import ba.tfb.tasknest.repository.projection.TaskerNotificationTarget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Salje mail taskerima o novom oglasu u njihovoj oblasti.
 * <p>
 * Nijedna greska ne izlazi iz ove klase, i to je namjerno. Notifikacije su vec
 * upisane u bazu kad se ovo pozove; ako bi izuzetak izasao do listenera, poruka
 * ne bi bila potvrdjena, RabbitMQ bi je ponovo isporucio i notifikacije bi se
 * duplirale. Mail je najbolji pokusaj, baza je trajni zapis.
 */
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
            // Hvata se po primaocu: jedna neispravna adresa ne smije zaustaviti ostale.
            try {
                mailSender.send(buildMessage(target, event));
            } catch (Exception e) {
                log.warn("Slanje maila na {} nije uspjelo za task {}: {}",
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
