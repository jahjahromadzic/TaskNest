package ba.tfb.tasknest.repository;

import ba.tfb.tasknest.entity.VerificationToken;
import ba.tfb.tasknest.entity.enums.VerificationTokenType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    Optional<VerificationToken> findByTokenAndType(String token, VerificationTokenType type);
}
