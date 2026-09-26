package ba.tfb.tasknest.dto.taskerprofile;

import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.entity.TaskerProfile;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record TaskerProfileResponse(
        UUID id,
        UUID userId,
        String fullName,
        String headline,
        String bio,
        boolean verified,
        BigDecimal averageRating,
        int completedJobsCount,
        List<String> categories,
        List<String> municipalities
) {
    public static TaskerProfileResponse from(TaskerProfile profile) {
        return new TaskerProfileResponse(
                profile.getId(),
                profile.getUser().getId(),
                profile.getUser().getFirstName() + " " + profile.getUser().getLastName(),
                profile.getHeadline(),
                profile.getBio(),
                profile.isVerified(),
                profile.getAverageRating(),
                profile.getCompletedJobsCount() == null ? 0 : profile.getCompletedJobsCount(),
                profile.getCategories().stream().map(Category::getName).sorted().toList(),
                profile.getMunicipalities().stream().map(Municipality::getName).sorted().toList()
        );
    }
}