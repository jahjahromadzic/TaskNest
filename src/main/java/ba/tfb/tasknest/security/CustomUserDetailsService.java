package ba.tfb.tasknest.security;

import ba.tfb.tasknest.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        return userRepository.findByEmail(email)
                .map(UserPrincipal::withCredentials)
                .orElseThrow(() ->
                        new UsernameNotFoundException("No user with email " + email));
    }

    /**
     * Ucitava korisnika za JWT putanju - svjeze iz baze, da suspenzija djeluje
     * odmah, a ne tek po isteku tokena. Bez hesa lozinke, tamo nije potreban.
     */
    @Transactional(readOnly = true)
    public Optional<UserPrincipal> loadUserById(UUID id) {
        return userRepository.findById(id).map(UserPrincipal::withoutCredentials);
    }
}
