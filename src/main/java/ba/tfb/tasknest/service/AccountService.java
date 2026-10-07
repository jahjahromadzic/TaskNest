package ba.tfb.tasknest.service;

import ba.tfb.tasknest.dto.account.AccountResponse;
import ba.tfb.tasknest.dto.account.UpdateAccountRequest;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.exception.ResourceNotFoundException;
import ba.tfb.tasknest.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccountService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public AccountResponse getAccount(UUID userId) {
        return AccountResponse.from(loadUser(userId));
    }

    @Transactional
    public AccountResponse updateAccount(UUID userId, UpdateAccountRequest request) {
        User user = loadUser(userId);

        user.setFirstName(request.firstName().strip());
        user.setLastName(request.lastName().strip());
        user.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().strip());

        return AccountResponse.from(user);
    }

    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }
}
