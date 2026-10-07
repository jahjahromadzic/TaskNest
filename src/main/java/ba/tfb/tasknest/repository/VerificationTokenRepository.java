package ba.tfb.tasknest.repository;

import ba.tfb.tasknest.entity.VerificationToken;
import ba.tfb.tasknest.entity.enums.VerificationTokenType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {

    @EntityGraph(attributePaths = "user")
    Optional<VerificationToken> findByTokenAndType(String token, VerificationTokenType type);

    @Modifying
    @Query("""
        update VerificationToken t set t.usedAt = :usedAt
        where t.user.id = :userId and t.type = :type and t.usedAt is null
        """)
    int markUnusedAsUsed(@Param("userId") UUID userId,
                         @Param("type") VerificationTokenType type,
                         @Param("usedAt") LocalDateTime usedAt);
}
