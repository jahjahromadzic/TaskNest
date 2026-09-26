package ba.tfb.tasknest.repository.projection;

import ba.tfb.tasknest.entity.enums.AccountStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record AdminUserRow(
        UUID id,
        String email,
        String firstName,
        String lastName,
        AccountStatus accountStatus,
        LocalDateTime createdAt
) {
}
