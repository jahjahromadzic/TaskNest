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
import ba.tfb.tasknest.repository.TaskerProfileRepository;
import ba.tfb.tasknest.repository.UserRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Poslovna pravila TaskerProfileService-a, bez Springa i baze.
 * <p>
 * Pokrivenost kategorijama i opstinama je preduslov za matching, pa je
 * tezisce na tome da se skup stvarno ZAMIJENI i da se neispravan ulaz odbije.
 */
@ExtendWith(MockitoExtension.class)
class TaskerProfileServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PROFILE_ID = UUID.randomUUID();
    private static final UUID CATEGORY_A = UUID.randomUUID();
    private static final UUID CATEGORY_B = UUID.randomUUID();
    private static final UUID OLD_CATEGORY_ID = UUID.randomUUID();
    private static final UUID MUNICIPALITY_A = UUID.randomUUID();
    private static final UUID MUNICIPALITY_B = UUID.randomUUID();
    private static final UUID OLD_MUNICIPALITY_ID = UUID.randomUUID();

    @Mock private TaskerProfileRepository taskerProfileRepository;
    @Mock private UserRepository userRepository;
    @Mock private CategoryRepository categoryRepository;
    @Mock private MunicipalityRepository municipalityRepository;

    @InjectMocks private TaskerProfileService taskerProfileService;

    @Nested
    class LoadingOwnProfile {

        @Test
        void updateCategories_throwsNotFound_whenUserDoesNotExist() {
            // Arrange
            when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

            // Act + Assert
            assertThatThrownBy(() -> taskerProfileService.updateCategories(USER_ID, coverage(CATEGORY_A)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("User");
        }

        @Test
        void updateCategories_throwsBusinessRule_whenUserHasNoTaskerProfile() {
            // Arrange - korisnik postoji, ali nikad nije aktivirao Tasker rolu
            User user = aUser();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(taskerProfileRepository.findByUser(user)).thenReturn(Optional.empty());

            // Act + Assert
            assertThatThrownBy(() -> taskerProfileService.updateCategories(USER_ID, coverage(CATEGORY_A)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("no tasker profile");
        }

        @Test
        void getMyProfile_throwsBusinessRule_whenUserHasNoTaskerProfile() {
            // Arrange
            User user = aUser();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(taskerProfileRepository.findByUser(user)).thenReturn(Optional.empty());

            // Act + Assert
            assertThatThrownBy(() -> taskerProfileService.getMyProfile(USER_ID))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    @Nested
    class UpdateCategories {

        @Test
        void updateCategories_throwsNotFound_whenNoCategoryMatchesGivenId() {
            // Arrange
            givenProfileExists(aProfile());
            when(categoryRepository.findAllById(Set.of(CATEGORY_A))).thenReturn(List.of());

            // Act + Assert
            assertThatThrownBy(() -> taskerProfileService.updateCategories(USER_ID, coverage(CATEGORY_A)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Category");
        }

        @Test
        void updateCategories_throwsNotFound_whenOnlySomeIdsExist() {
            // Arrange - findAllById tiho preskace nenadjene, pa se trazena i
            // vracena velicina moraju porediti; inace bi pogresan ID prosao neopazeno.
            TaskerProfile profile = aProfile();
            givenProfileExists(profile);
            when(categoryRepository.findAllById(Set.of(CATEGORY_A, CATEGORY_B)))
                    .thenReturn(List.of(aCategory(CATEGORY_A, "Vodoinstalacije", true)));

            // Act + Assert - u poruci smije biti samo ID koji fali, ne i onaj ispravan
            assertThatThrownBy(() ->
                    taskerProfileService.updateCategories(USER_ID, coverage(CATEGORY_A, CATEGORY_B)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(CATEGORY_B.toString())
                    .hasMessageNotContaining(CATEGORY_A.toString());

            assertThat(profile.getCategories())
                    .as("neuspjela izmjena ne smije dirati postojecu pokrivenost")
                    .hasSize(1);
        }

        @Test
        void updateCategories_throwsBusinessRule_whenAnyCategoryIsInactive() {
            // Arrange - jedna aktivna, jedna ugasena
            TaskerProfile profile = aProfile();
            givenProfileExists(profile);
            when(categoryRepository.findAllById(Set.of(CATEGORY_A, CATEGORY_B)))
                    .thenReturn(List.of(
                            aCategory(CATEGORY_A, "Vodoinstalacije", true),
                            aCategory(CATEGORY_B, "Ugasena", false)));

            // Act + Assert
            assertThatThrownBy(() ->
                    taskerProfileService.updateCategories(USER_ID, coverage(CATEGORY_A, CATEGORY_B)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("not active")
                    .hasMessageContaining("Ugasena");

            assertThat(profile.getCategories()).hasSize(1);
        }

        @Test
        void updateCategories_replacesPreviousSelection_whenIdsAreValid() {
            // Arrange - profil vec pokriva staru kategoriju
            TaskerProfile profile = aProfile();
            givenProfileExists(profile);
            when(categoryRepository.findAllById(Set.of(CATEGORY_A, CATEGORY_B)))
                    .thenReturn(List.of(
                            aCategory(CATEGORY_A, "Vodoinstalacije", true),
                            aCategory(CATEGORY_B, "Elektroinstalacije", true)));

            // Act
            TaskerProfileResponse response =
                    taskerProfileService.updateCategories(USER_ID, coverage(CATEGORY_A, CATEGORY_B));

            // Assert - zamjena, ne dopisivanje: stara kategorija mora nestati
            assertThat(response.categories())
                    .containsExactly("Elektroinstalacije", "Vodoinstalacije");
            assertThat(profile.getCategories()).hasSize(2);
            assertThat(profile.getCategories())
                    .extracting(Category::getName)
                    .doesNotContain("Stara kategorija");
        }

        /**
         * Pravilo garantuje servis, ne @NotEmpty na DTO-u: nista se ne ucitava ni
         * ne mijenja prije provjere, pa ni pozivalac mimo kontrolera ne moze
         * praznim skupom obrisati pokrivenost.
         */
        @Test
        void updateCategories_throwsBusinessRule_whenIdSetIsEmpty() {
            // Arrange - namjerno bez ijednog stuba: provjera je prije svakog upita

            // Act + Assert
            assertThatThrownBy(() ->
                    taskerProfileService.updateCategories(USER_ID, new UpdateCoverageRequest(Set.of())))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("At least one category");
        }

        @Test
        void updateCategories_throwsBusinessRule_whenIdSetIsNull() {
            // Arrange - null je ranije bio NPE

            // Act + Assert
            assertThatThrownBy(() ->
                    taskerProfileService.updateCategories(USER_ID, new UpdateCoverageRequest(null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("At least one category");
        }
    }

    @Nested
    class UpdateMunicipalities {

        @Test
        void updateMunicipalities_throwsNotFound_whenOnlySomeIdsExist() {
            // Arrange
            TaskerProfile profile = aProfile();
            givenProfileExists(profile);
            when(municipalityRepository.findAllById(Set.of(MUNICIPALITY_A, MUNICIPALITY_B)))
                    .thenReturn(List.of(aMunicipality(MUNICIPALITY_A, "Centar")));

            // Act + Assert
            assertThatThrownBy(() ->
                    taskerProfileService.updateMunicipalities(USER_ID, coverage(MUNICIPALITY_A, MUNICIPALITY_B)))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Municipality");

            assertThat(profile.getMunicipalities()).hasSize(1);
        }

        @Test
        void updateMunicipalities_throwsBusinessRule_whenIdSetIsEmpty() {
            // Act + Assert
            assertThatThrownBy(() ->
                    taskerProfileService.updateMunicipalities(USER_ID, new UpdateCoverageRequest(Set.of())))
                    .isInstanceOf(BusinessRuleException.class)
                    .hasMessageContaining("At least one municipality");
        }

        @Test
        void updateMunicipalities_replacesPreviousSelection_whenIdsAreValid() {
            // Arrange
            TaskerProfile profile = aProfile();
            givenProfileExists(profile);
            when(municipalityRepository.findAllById(Set.of(MUNICIPALITY_A, MUNICIPALITY_B)))
                    .thenReturn(List.of(
                            aMunicipality(MUNICIPALITY_A, "Centar"),
                            aMunicipality(MUNICIPALITY_B, "Ilidza")));

            // Act
            TaskerProfileResponse response =
                    taskerProfileService.updateMunicipalities(USER_ID, coverage(MUNICIPALITY_A, MUNICIPALITY_B));

            // Assert
            assertThat(response.municipalities()).containsExactly("Centar", "Ilidza");
            assertThat(profile.getMunicipalities())
                    .extracting(Municipality::getName)
                    .doesNotContain("Stara opstina");
        }
    }

    @Nested
    class UpdateProfile {

        @Test
        void updateProfile_updatesHeadlineAndBio_whenProfileExists() {
            // Arrange
            TaskerProfile profile = aProfile();
            givenProfileExists(profile);

            // Act
            TaskerProfileResponse response = taskerProfileService.updateProfile(
                    USER_ID, new UpdateTaskerProfileRequest("Vodoinstalater", "15 godina iskustva"));

            // Assert
            assertThat(response.headline()).isEqualTo("Vodoinstalater");
            assertThat(response.bio()).isEqualTo("15 godina iskustva");
        }

        @Test
        void updateProfile_throwsBusinessRule_whenUserHasNoTaskerProfile() {
            // Arrange
            User user = aUser();
            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
            when(taskerProfileRepository.findByUser(user)).thenReturn(Optional.empty());

            // Act + Assert
            assertThatThrownBy(() -> taskerProfileService.updateProfile(
                    USER_ID, new UpdateTaskerProfileRequest("X", "Y")))
                    .isInstanceOf(BusinessRuleException.class);
        }
    }

    @Nested
    class GetProfile {

        @Test
        void getProfile_throwsNotFound_whenProfileDoesNotExist() {
            // Arrange
            when(taskerProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.empty());

            // Act + Assert
            assertThatThrownBy(() -> taskerProfileService.getProfile(PROFILE_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("TaskerProfile");
        }

        @Test
        void getProfile_returnsPublicView_whenProfileExists() {
            // Arrange
            when(taskerProfileRepository.findById(PROFILE_ID)).thenReturn(Optional.of(aProfile()));

            // Act
            TaskerProfileResponse response = taskerProfileService.getProfile(PROFILE_ID);

            // Assert
            assertThat(response.id()).isEqualTo(PROFILE_ID);
            assertThat(response.fullName()).isEqualTo("Mirza Tasker");
            assertThat(response.categories()).containsExactly("Stara kategorija");
        }
    }

    // ---------- fixtures ----------

    private void givenProfileExists(TaskerProfile profile) {
        User user = profile.getUser();
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(taskerProfileRepository.findByUser(user)).thenReturn(Optional.of(profile));
    }

    private UpdateCoverageRequest coverage(UUID... ids) {
        return new UpdateCoverageRequest(Set.of(ids));
    }

    private User aUser() {
        User user = new User();
        user.setId(USER_ID);
        user.setFirstName("Mirza");
        user.setLastName("Tasker");
        return user;
    }

    /** Profil koji vec ima po jednu kategoriju i opstinu, da se vidi zamjena. */
    private TaskerProfile aProfile() {
        TaskerProfile profile = new TaskerProfile();
        profile.setId(PROFILE_ID);
        profile.setUser(aUser());
        profile.setCompletedJobsCount(0);
        profile.getCategories().add(aCategory(OLD_CATEGORY_ID, "Stara kategorija", true));
        profile.getMunicipalities().add(aMunicipality(OLD_MUNICIPALITY_ID, "Stara opstina"));
        return profile;
    }

    private Category aCategory(UUID id, String name, boolean active) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setActive(active);
        return category;
    }

    private Municipality aMunicipality(UUID id, String name) {
        Municipality municipality = new Municipality();
        municipality.setId(id);
        municipality.setName(name);
        return municipality;
    }
}
