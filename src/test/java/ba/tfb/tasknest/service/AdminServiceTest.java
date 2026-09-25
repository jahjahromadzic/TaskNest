package ba.tfb.tasknest.service;

import ba.tfb.tasknest.dto.admin.AdminUserResponse;
import ba.tfb.tasknest.entity.Role;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.AccountStatus;
import ba.tfb.tasknest.entity.enums.RoleName;
import ba.tfb.tasknest.exception.BusinessRuleException;
import ba.tfb.tasknest.repository.RefreshTokenRepository;
import ba.tfb.tasknest.repository.TaskerProfileRepository;
import ba.tfb.tasknest.repository.UserRepository;
import ba.tfb.tasknest.repository.projection.AdminUserRow;
import ba.tfb.tasknest.repository.projection.UserRoleRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    private static final UUID ADMIN_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 15, 12, 0);

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private TaskerProfileRepository taskerProfileRepository;
    @Mock private TaskService taskService;

    private AdminService adminService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        adminService = new AdminService(userRepository, refreshTokenRepository,
                taskerProfileRepository, taskService, clock);
    }

    @Nested
    class Suspend {

        @Test
        void suspendUser_suspendsAndRevokesTokens_whenTargetIsARegularUser() {
            // Arrange
            User user = aUser(USER_ID, RoleName.CLIENT);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            // Act
            AdminUserResponse response = adminService.suspendUser(ADMIN_ID, USER_ID);

            // Assert - opoziv je bulk upit, pa je poziv dio ugovora
            assertThat(response.accountStatus()).isEqualTo(AccountStatus.SUSPENDED);
            verify(refreshTokenRepository).revokeAllByUser(USER_ID, NOW);
        }

        @Test
        void suspendUser_throwsBusinessRule_whenAdminTargetsThemselves() {
            // Act + Assert - sistem ne smije ostati bez admina zbog jednog klika
            assertThatThrownBy(() -> adminService.suspendUser(ADMIN_ID, ADMIN_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("your own");
        }

        @Test
        void suspendUser_throwsBusinessRule_whenTargetIsAnotherAdmin() {
            // Arrange - ukraden admin token ne smije zakljucati ostale
            User otherAdmin = aUser(USER_ID, RoleName.CLIENT, RoleName.ADMIN);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(otherAdmin));

            // Act + Assert
            assertThatThrownBy(() -> adminService.suspendUser(ADMIN_ID, USER_ID))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("Administrators");
            assertThat(otherAdmin.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
            verify(refreshTokenRepository, never()).revokeAllByUser(any(), any());
        }
    }

    @Nested
    class Reactivate {

        @Test
        void reactivateUser_restoresActive_whenUserWasSuspended() {
            // Arrange
            User user = aUser(USER_ID, RoleName.CLIENT);
            user.setAccountStatus(AccountStatus.SUSPENDED);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            // Act
            AdminUserResponse response = adminService.reactivateUser(ADMIN_ID, USER_ID);

            // Assert
            assertThat(response.accountStatus()).isEqualTo(AccountStatus.ACTIVE);
        }

        @Test
        void reactivateUser_throwsBusinessRule_whenUserDeactivatedThemselves() {
            // Arrange - to je korisnikova odluka, ne moderatorska
            User user = aUser(USER_ID, RoleName.CLIENT);
            user.setAccountStatus(AccountStatus.DEACTIVATED);
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            // Act + Assert
            assertThatThrownBy(() -> adminService.reactivateUser(ADMIN_ID, USER_ID))
                    .isInstanceOf(BusinessRuleException.class);
            assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.DEACTIVATED);
        }
    }

    @Nested
    class ListUsers {

        @Test
        void listUsers_attachesRolesFromASingleQuery() {
            // Arrange
            UUID second = UUID.randomUUID();
            when(userRepository.findForAdmin(eq(null), eq(""), any()))
                    .thenReturn(new PageImpl<>(List.of(aRow(USER_ID), aRow(second))));
            when(userRepository.findRolesFor(anyCollection())).thenReturn(List.of(
                    new UserRoleRow(USER_ID, RoleName.CLIENT),
                    new UserRoleRow(USER_ID, RoleName.TASKER),
                    new UserRoleRow(second, RoleName.CLIENT)));

            // Act
            List<AdminUserResponse> users =
                    adminService.listUsers(null, null, PageRequest.of(0, 20)).getContent();

            // Assert
            assertThat(users.get(0).roles()).containsExactlyInAnyOrder(RoleName.CLIENT, RoleName.TASKER);
            assertThat(users.get(1).roles()).containsExactly(RoleName.CLIENT);
        }

        @Test
        void listUsers_normalizesTheEmailFilter() {
            // Arrange - emailovi su u bazi mala slova
            when(userRepository.findForAdmin(eq(AccountStatus.SUSPENDED), eq("amra@test"), any()))
                    .thenReturn(Page.empty());

            // Act
            var page = adminService.listUsers(AccountStatus.SUSPENDED, "  Amra@Test ", PageRequest.of(0, 20));

            // Assert - i prazna stranica ne salje upit za role
            assertThat(page).isEmpty();
            verify(userRepository, never()).findRolesFor(anyCollection());
        }
    }

    // ---------- fixtures ----------

    private User aUser(UUID id, RoleName... roleNames) {
        User user = new User();
        user.setId(id);
        user.setEmail("user@test.ba");
        user.setFirstName("Test");
        user.setLastName("User");
        for (RoleName name : roleNames) {
            Role role = new Role();
            role.setName(name);
            user.getRoles().add(role);
        }
        return user;
    }

    private AdminUserRow aRow(UUID id) {
        return new AdminUserRow(id, id + "@test.ba", "Test", "User", AccountStatus.ACTIVE, NOW);
    }
}
