package ba.tfb.tasknest.repository;

import ba.tfb.tasknest.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByToken(String token);

    /** Bez @Modifying Spring Data izvrsava upit kao SELECT i DML puca u runtime-u. */
    @Modifying
    @Query("""
            update RefreshToken rt
            set rt.revokedAt = :revokedAt
            where rt.user.id = :userId and rt.revokedAt is null
            """)
    int revokeAllByUser(@Param("userId") UUID userId, @Param("revokedAt") LocalDateTime revokedAt);
}
