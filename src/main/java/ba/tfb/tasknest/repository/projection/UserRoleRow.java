package ba.tfb.tasknest.repository.projection;

import ba.tfb.tasknest.entity.enums.RoleName;

import java.util.UUID;

/** Jedan par (korisnik, rola) - za role cijele stranice u jednom upitu. */
public record UserRoleRow(
        UUID userId,
        RoleName role
) {
}
