package ba.tfb.tasknest.repository.projection;

import ba.tfb.tasknest.entity.enums.RoleName;

import java.util.UUID;

public record UserRoleRow(
        UUID userId,
        RoleName role
) {
}
