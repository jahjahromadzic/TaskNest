package ba.tfb.tasknest.service;

import ba.tfb.tasknest.entity.User;

public record IssuedRefreshToken(
        User user,
        String value
) {
}
