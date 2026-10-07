package ba.tfb.tasknest.dto.account;

import ba.tfb.tasknest.entity.User;

import java.time.LocalDateTime;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String email,
        String firstName,
        String lastName,
        String phone,
        LocalDateTime memberSince
) {

    public static AccountResponse from(User user) {
        return new AccountResponse(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(),
                user.getPhone(), user.getCreatedAt());
    }
}
