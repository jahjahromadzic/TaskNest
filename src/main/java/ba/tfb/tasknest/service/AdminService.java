package ba.tfb.tasknest.service;

import ba.tfb.tasknest.dto.admin.AdminUserResponse;
import ba.tfb.tasknest.dto.task.TaskResponse;
import ba.tfb.tasknest.dto.taskerprofile.TaskerProfileResponse;
import ba.tfb.tasknest.entity.TaskerProfile;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.AccountStatus;
import ba.tfb.tasknest.entity.enums.RoleName;
import ba.tfb.tasknest.exception.BusinessRuleException;
import ba.tfb.tasknest.exception.ResourceNotFoundException;
import ba.tfb.tasknest.repository.RefreshTokenRepository;
import ba.tfb.tasknest.repository.TaskerProfileRepository;
import ba.tfb.tasknest.repository.UserRepository;
import ba.tfb.tasknest.repository.projection.AdminUserRow;
import ba.tfb.tasknest.repository.projection.UserRoleRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TaskerProfileRepository taskerProfileRepository;
    private final TaskService taskService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> listUsers(AccountStatus status, String email, Pageable pageable) {
        String emailFilter = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        Page<AdminUserRow> page = userRepository.findForAdmin(status, emailFilter, pageable);

        if (page.isEmpty()) {
            return page.map(row -> toResponse(row, Set.of()));
        }

        List<UUID> ids = page.getContent().stream().map(AdminUserRow::id).toList();
        Map<UUID, Set<RoleName>> roles = userRepository.findRolesFor(ids).stream()
                .collect(Collectors.groupingBy(UserRoleRow::userId,
                        Collectors.mapping(UserRoleRow::role,
                                Collectors.toCollection(() -> EnumSet.noneOf(RoleName.class)))));

        return page.map(row -> toResponse(row, roles.getOrDefault(row.id(), Set.of())));
    }

    @Transactional
    public AdminUserResponse suspendUser(UUID adminId, UUID userId) {
        if (adminId.equals(userId)) {
            throw new BusinessRuleException("You cannot suspend your own account");
        }

        User user = loadUser(userId);

        if (isAdmin(user)) {
            throw new BusinessRuleException("Administrators cannot be suspended through the API");
        }

        user.setAccountStatus(AccountStatus.SUSPENDED);
        int revoked = refreshTokenRepository.revokeAllByUser(userId, LocalDateTime.now(clock));

        log.info("Admin {} suspended user {} ({} refresh tokens revoked)", adminId, userId, revoked);
        return toResponse(user);
    }

    @Transactional
    public AdminUserResponse reactivateUser(UUID adminId, UUID userId) {
        User user = loadUser(userId);

        if (user.getAccountStatus() == AccountStatus.DEACTIVATED) {
            throw new BusinessRuleException("A deactivated account can only be restored by its owner");
        }

        user.setAccountStatus(AccountStatus.ACTIVE);

        log.info("Admin {} reactivated user {}", adminId, userId);
        return toResponse(user);
    }

    @Transactional
    public TaskerProfileResponse setTaskerVerified(UUID adminId, UUID profileId, boolean verified) {
        TaskerProfile profile = taskerProfileRepository.findById(profileId)
                .orElseThrow(() -> new ResourceNotFoundException("TaskerProfile", profileId));

        profile.setVerified(verified);

        log.info("Admin {} set verified={} on tasker profile {}", adminId, verified, profileId);
        return TaskerProfileResponse.from(profile);
    }

    @Transactional
    public TaskResponse removeTask(UUID adminId, UUID taskId, String reason) {
        TaskResponse removed = taskService.removeTask(taskId, reason);

        log.info("Admin {} removed task {}: {}", adminId, taskId, reason);
        return removed;
    }

    private User loadUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private static boolean isAdmin(User user) {
        return user.getRoles().stream().anyMatch(role -> role.getName() == RoleName.ADMIN);
    }

    private static AdminUserResponse toResponse(User user) {
        Set<RoleName> roles = user.getRoles().stream()
                .map(role -> role.getName())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(RoleName.class)));

        return new AdminUserResponse(user.getId(), user.getEmail(),
                user.getFirstName() + " " + user.getLastName(),
                user.getAccountStatus(), roles, user.getCreatedAt());
    }

    private static AdminUserResponse toResponse(AdminUserRow row, Set<RoleName> roles) {
        return new AdminUserResponse(row.id(), row.email(),
                row.firstName() + " " + row.lastName(),
                row.accountStatus(), roles, row.createdAt());
    }
}
