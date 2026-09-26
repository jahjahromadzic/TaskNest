package ba.tfb.tasknest.service;

import ba.tfb.tasknest.dto.taskerprofile.TaskerProfileResponse;
import ba.tfb.tasknest.dto.taskerprofile.UpdateCoverageRequest;
import ba.tfb.tasknest.dto.taskerprofile.UpdateTaskerProfileRequest;
import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.entity.TaskerProfile;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.exception.BusinessRuleException;
import ba.tfb.tasknest.exception.ResourceNotFoundException;
import ba.tfb.tasknest.repository.CategoryRepository;
import ba.tfb.tasknest.repository.MunicipalityRepository;
import ba.tfb.tasknest.repository.ReviewRepository;
import ba.tfb.tasknest.repository.TaskerProfileRepository;
import ba.tfb.tasknest.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TaskerProfileService {

    private final TaskerProfileRepository taskerProfileRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final MunicipalityRepository municipalityRepository;
    private final ReviewRepository reviewRepository;

    @Transactional
    public TaskerProfileResponse updateProfile(UUID userId,
                                               UpdateTaskerProfileRequest request) {
        TaskerProfile profile = loadOwnProfile(userId);

        profile.setHeadline(request.headline());
        profile.setBio(request.bio());

        return TaskerProfileResponse.from(profile);
    }

    @Transactional
    public TaskerProfileResponse updateCategories(UUID userId,
                                                  UpdateCoverageRequest request) {
        Set<UUID> requestedIds = requireNonEmpty(request, "category");

        TaskerProfile profile = loadOwnProfile(userId);

        Set<Category> categories = new HashSet<>(categoryRepository.findAllById(requestedIds));
        requireAllFound(requestedIds, categories, Category::getId, "Category");

        Set<Category> inactive = categories.stream()
                .filter(category -> !category.isActive())
                .collect(Collectors.toSet());
        if (!inactive.isEmpty()) {
            throw new BusinessRuleException("These categories are not active: "
                    + inactive.stream().map(Category::getName).sorted().toList());
        }

        profile.getCategories().clear();
        profile.getCategories().addAll(categories);

        return TaskerProfileResponse.from(profile);
    }

    @Transactional
    public TaskerProfileResponse updateMunicipalities(UUID userId,
                                                      UpdateCoverageRequest request) {
        Set<UUID> requestedIds = requireNonEmpty(request, "municipality");

        TaskerProfile profile = loadOwnProfile(userId);

        Set<Municipality> municipalities =
                new HashSet<>(municipalityRepository.findAllById(requestedIds));
        requireAllFound(requestedIds, municipalities, Municipality::getId, "Municipality");

        profile.getMunicipalities().clear();
        profile.getMunicipalities().addAll(municipalities);

        return TaskerProfileResponse.from(profile);
    }

    @Transactional
    public void recordCompletedJob(User tasker) {
        TaskerProfile profile = taskerProfileRepository.findByUser(tasker)
                .orElseThrow(() -> new IllegalStateException(
                        "Tasker has an accepted offer but no profile: " + tasker.getId()));

        profile.setCompletedJobsCount(profile.getCompletedJobsCount() + 1);
    }

    @Transactional
    public void refreshAverageRating(User reviewee) {
        taskerProfileRepository.findWithWriteLockByUser(reviewee).ifPresent(profile -> {
            BigDecimal average = reviewRepository.findAverageRatingByReviewee(reviewee.getId())
                    .map(value -> BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP))
                    .orElse(null);

            profile.setAverageRating(average);
        });
    }

    @Transactional(readOnly = true)
    public TaskerProfileResponse getMyProfile(UUID userId) {
        return TaskerProfileResponse.from(loadOwnProfile(userId));
    }

    @Transactional(readOnly = true)
    public TaskerProfileResponse getProfile(UUID profileId) {
        TaskerProfile profile = taskerProfileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("TaskerProfile", profileId));

        return TaskerProfileResponse.from(profile);
    }

    private Set<UUID> requireNonEmpty(UpdateCoverageRequest request, String what) {
        if (request == null || request.ids() == null || request.ids().isEmpty()) {
            throw new BusinessRuleException(
                    "At least one " + what + " must be selected");
        }
        return request.ids();
    }

    private <T> void requireAllFound(Set<UUID> requestedIds,
                                     Set<T> found,
                                     Function<T, UUID> idOf,
                                     String resourceName) {
        if (found.size() == requestedIds.size()) {
            return;
        }

        Set<UUID> missing = new HashSet<>(requestedIds);
        found.forEach(entity -> missing.remove(idOf.apply(entity)));

        throw new ResourceNotFoundException(resourceName, missing);
    }

    private TaskerProfile loadOwnProfile(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        return taskerProfileRepository.findByUser(user)
                .orElseThrow(() -> new BusinessRuleException(
                        "This account has no tasker profile - activate the tasker role first"));
    }
}
