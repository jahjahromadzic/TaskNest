package ba.tfb.tasknest.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
@Slf4j
public class PasswordResetMailer {

    private final JavaMailSender mailSender;
    private final String from;
    private final String frontendUrl;

    public PasswordResetMailer(JavaMailSender mailSender,
                               @Value("${app.mail.from}") String from,
                               @Value("${app.frontend-url}") String frontendUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.frontendUrl = frontendUrl;
    }

    public void sendResetLink(String email, String firstName, String token, long validMinutes) {
        String link = UriComponentsBuilder.fromUriString(frontendUrl)
                .path("/reset-password")
                .queryParam("token", token)
                .toUriString();

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Reset your TaskNest password");
        message.setText("""
                Hi %s,

                Someone asked to reset the password of your TaskNest account. If it was you, open this link
                and choose a new password:

                  %s

                The link works once and expires in %d minutes. If you did not ask for it, ignore this email;
                your password stays the same.
                """.formatted(firstName, link, validMinutes));

        try {
            mailSender.send(message);
        } catch (Exception e) {
            log.warn("Sending the password reset mail to {} failed: {}", email, e.getMessage());
        }
    }
}
