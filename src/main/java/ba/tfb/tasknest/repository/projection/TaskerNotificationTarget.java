package ba.tfb.tasknest.repository.projection;

import java.util.UUID;

/**
 * Ono i samo ono sto notifikacija o novom tasku treba o taskeru.
 * <p>
 * Namjerno nije TaskerProfile: entitet vuce user, categories i municipalities
 * kao LAZY veze, pa bi iteracija po 50 taskera znacila preko 150 upita. Ovdje
 * je jedan upit i tri kolone.
 */
public record TaskerNotificationTarget(
        UUID userId,
        String email,
        String fullName
) {
}
