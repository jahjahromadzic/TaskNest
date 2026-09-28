package ba.tfb.tasknest.service;

import ba.tfb.tasknest.dto.client.ClientHireResponse;
import ba.tfb.tasknest.dto.client.ClientProfileResponse;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.exception.ResourceNotFoundException;
import ba.tfb.tasknest.repository.ReviewRepository;
import ba.tfb.tasknest.repository.TaskRepository;
import ba.tfb.tasknest.repository.UserRepository;
import ba.tfb.tasknest.repository.projection.ClientTaskStats;
import ba.tfb.tasknest.repository.projection.RatingSummary;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ClientProfileService {

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final ReviewRepository reviewRepository;

    @Transactional(readOnly = true)
    public ClientProfileResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        RatingSummary rating = reviewRepository.summarizeReceivedAsClient(userId);
        ClientTaskStats stats = taskRepository.statsOfClient(userId);

        return new ClientProfileResponse(
                user.getId(),
                user.getFirstName() + " " + user.getLastName(),
                user.getCreatedAt(),
                rating.average() == null ? null
                        : BigDecimal.valueOf(rating.average()).setScale(2, RoundingMode.HALF_UP),
                rating.count(),
                stats.posted(),
                stats.hires(),
                stats.completed(),
                stats.cancelled());
    }

    @Transactional(readOnly = true)
    public Page<ClientHireResponse> getHires(UUID userId, Pageable pageable) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User", userId);
        }
        return taskRepository.findHiresOfClient(userId, pageable);
    }
}
