package ba.tfb.tasknest.bootstrap;

import ba.tfb.tasknest.entity.Role;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.RoleName;
import ba.tfb.tasknest.repository.RoleRepository;
import ba.tfb.tasknest.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

/**
 * Kako nastaje prvi admin.
 * <p>
 * Registracija ga ne smije praviti, a endpoint "napravi admina" trazi da admin
 * vec postoji. Zato: nalog se registruje normalno, a app.admin.email ga pri
 * pokretanju unaprijedi. Lozinka nikad ne prolazi kroz konfiguraciju, i u
 * repozitoriju nema kredencijala.
 * <p>
 * Idempotentno: nalog koji je vec admin ostaje takav, pa je bezbjedno na svakom
 * restartu. Nalog registrovan nakon pokretanja postaje admin tek na sljedecem.
 */
@Component
@Slf4j
public class AdminBootstrap {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final String adminEmail;

    public AdminBootstrap(UserRepository userRepository,
                          RoleRepository roleRepository,
                          @Value("${app.admin.email:}") String adminEmail) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.adminEmail = adminEmail;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void promoteConfiguredAdmin() {
        if (adminEmail == null || adminEmail.isBlank()) {
            return;
        }

        String email = adminEmail.trim().toLowerCase(Locale.ROOT);
        Optional<User> candidate = userRepository.findByEmail(email);

        if (candidate.isEmpty()) {
            // Upozorenje, ne greska: aplikacija radi i bez admina, a nalog se
            // moze registrovati pa pokupiti na sljedecem restartu.
            log.warn("app.admin.email is set to {} but no such account exists; register it and restart", email);
            return;
        }

        User user = candidate.get();
        boolean alreadyAdmin = user.getRoles().stream().anyMatch(role -> role.getName() == RoleName.ADMIN);
        if (alreadyAdmin) {
            return;
        }

        Role adminRole = roleRepository.findByName(RoleName.ADMIN)
                .orElseThrow(() -> new IllegalStateException("ADMIN role is missing"));
        user.getRoles().add(adminRole);

        log.info("Granted ADMIN role to {}", email);
    }
}
