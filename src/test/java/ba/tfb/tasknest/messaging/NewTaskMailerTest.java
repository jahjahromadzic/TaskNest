package ba.tfb.tasknest.messaging;

import ba.tfb.tasknest.repository.projection.TaskerNotificationTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NewTaskMailerTest {

    private static final TaskPublishedEvent EVENT = new TaskPublishedEvent(
            UUID.randomUUID(), "Popravka slavine",
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    @Mock private JavaMailSender mailSender;

    @Test
    void sendNewTaskEmails_sendsOneMessagePerTarget_whenTargetsExist() {
        // Arrange
        NewTaskMailer mailer = new NewTaskMailer(mailSender, "noreply@tasknest.ba");

        // Act
        mailer.sendNewTaskEmails(List.of(target("a@test.ba", "Mirza Tasker"),
                target("b@test.ba", "Selma Rival")), EVENT);

        // Assert
        ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(2)).send(sent.capture());

        assertThat(sent.getAllValues())
                .extracting(message -> message.getTo()[0])
                .containsExactly("a@test.ba", "b@test.ba");
        assertThat(sent.getAllValues().getFirst().getSubject()).contains("Popravka slavine");
        assertThat(sent.getAllValues().getFirst().getFrom()).isEqualTo("noreply@tasknest.ba");
    }

    @Test
    void sendNewTaskEmails_swallowsFailure_whenMailServerIsDown() {
        // Arrange
        NewTaskMailer mailer = new NewTaskMailer(mailSender, "noreply@tasknest.ba");
        doThrow(new MailSendException("connection refused")).when(mailSender).send(any(SimpleMailMessage.class));

        // Act + Assert
        assertThatCode(() -> mailer.sendNewTaskEmails(List.of(target("a@test.ba", "Mirza")), EVENT))
                .doesNotThrowAnyException();
    }

    @Test
    void sendNewTaskEmails_continuesWithOthers_whenOneRecipientFails() {
        // Arrange
        NewTaskMailer mailer = new NewTaskMailer(mailSender, "noreply@tasknest.ba");
        doThrow(new MailSendException("bad address"))
                .doNothing()
                .when(mailSender).send(any(SimpleMailMessage.class));

        // Act
        mailer.sendNewTaskEmails(List.of(target("bad@test.ba", "Prvi"),
                target("good@test.ba", "Drugi")), EVENT);

        // Assert
        verify(mailSender, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendNewTaskEmails_sendsNothing_whenThereAreNoTargets() {
        // Arrange
        NewTaskMailer mailer = new NewTaskMailer(mailSender, "noreply@tasknest.ba");

        // Act
        mailer.sendNewTaskEmails(List.of(), EVENT);

        // Assert
        verify(mailSender, times(0)).send(any(SimpleMailMessage.class));
    }

    private TaskerNotificationTarget target(String email, String fullName) {
        return new TaskerNotificationTarget(UUID.randomUUID(), email, fullName);
    }
}
