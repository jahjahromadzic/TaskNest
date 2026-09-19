package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.dto.taskerprofile.TaskerProfileResponse;
import ba.tfb.tasknest.dto.taskerprofile.UpdateCoverageRequest;
import ba.tfb.tasknest.dto.taskerprofile.UpdateTaskerProfileRequest;
import ba.tfb.tasknest.security.UserPrincipal;
import ba.tfb.tasknest.service.TaskerProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/tasker-profiles")
@RequiredArgsConstructor
public class TaskerProfileController {

    private final TaskerProfileService taskerProfileService;

    @GetMapping("/me")
    @PreAuthorize("hasRole('TASKER')")
    public TaskerProfileResponse myProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return taskerProfileService.getMyProfile(principal.getId());
    }

    @PutMapping("/me")
    @PreAuthorize("hasRole('TASKER')")
    public TaskerProfileResponse updateProfile(
            @Valid @RequestBody UpdateTaskerProfileRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return taskerProfileService.updateProfile(principal.getId(), request);
    }

    @PutMapping("/me/categories")
    @PreAuthorize("hasRole('TASKER')")
    public TaskerProfileResponse updateCategories(
            @Valid @RequestBody UpdateCoverageRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return taskerProfileService.updateCategories(principal.getId(), request);
    }

    @PutMapping("/me/municipalities")
    @PreAuthorize("hasRole('TASKER')")
    public TaskerProfileResponse updateMunicipalities(
            @Valid @RequestBody UpdateCoverageRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return taskerProfileService.updateMunicipalities(principal.getId(), request);
    }

    @GetMapping("/{id}")
    public TaskerProfileResponse getProfile(@PathVariable UUID id) {
        return taskerProfileService.getProfile(id);
    }
}