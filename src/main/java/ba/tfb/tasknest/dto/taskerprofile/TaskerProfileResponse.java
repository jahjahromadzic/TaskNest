package ba.tfb.tasknest.dto.taskerprofile;

import ba.tfb.tasknest.dto.reference.CategoryResponse;
import ba.tfb.tasknest.dto.reference.MunicipalityResponse;
import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.entity.TaskerProfile;

import java.math.BigDecimal;
import java.util.Comparator;
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
        int withdrawnJobsCount,
        List<CategoryResponse> categories,
        List<MunicipalityResponse> municipalities
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
                profile.getWithdrawnJobsCount() == null ? 0 : profile.getWithdrawnJobsCount(),
                profile.getCategories().stream()
                        .sorted(Comparator.comparing(Category::getName))
                        .map(CategoryResponse::from)
                        .toList(),
                profile.getMunicipalities().stream()
                        .sorted(Comparator.comparing(Municipality::getName))
                        .map(MunicipalityResponse::from)
                        .toList()
        );
    }
}