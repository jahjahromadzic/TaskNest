package ba.tfb.tasknest.dto.admin;

import ba.tfb.tasknest.entity.enums.AccountStatus;
import ba.tfb.tasknest.entity.enums.RoleName;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

public record AdminUserResponse(
        UUID id,
        String email,
        String fullName,
        AccountStatus accountStatus,
        Set<RoleName> roles,
        LocalDateTime createdAt
) {
}
