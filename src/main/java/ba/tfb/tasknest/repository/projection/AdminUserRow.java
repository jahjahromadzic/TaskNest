package ba.tfb.tasknest.repository.projection;

import ba.tfb.tasknest.entity.enums.AccountStatus;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Red liste korisnika za admina, bez entiteta.
 * <p>
 * User ucitava role EAGER, pa bi stranica od 20 entiteta povukla jos 20 upita
 * za role. Projekcija ne ucitava entitet uopste; role za cijelu stranicu dolaze
 * jednim dodatnim upitom.
 */
public record AdminUserRow(
        UUID id,
        String email,
        String firstName,
        String lastName,
        AccountStatus accountStatus,
        LocalDateTime createdAt
) {
}
