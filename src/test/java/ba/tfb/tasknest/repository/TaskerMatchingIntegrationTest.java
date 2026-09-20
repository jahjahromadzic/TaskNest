package ba.tfb.tasknest.repository;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.entity.TaskerProfile;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.AccountStatus;
import ba.tfb.tasknest.repository.projection.TaskerNotificationTarget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Matching upit protiv prave baze. Filter na neaktivne kategorije zivi u JPQL-u,
 * pa se mockovima ne moze provjeriti - ovaj test je jedini dokaz da radi.
 */
class TaskerMatchingIntegrationTest extends AbstractIntegrationTest {

    @Autowired private TaskerProfileRepository taskerProfileRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MunicipalityRepository municipalityRepository;

    /** Vlasnik oglasa koji nije nas tasker - da ga iskljucenje klijenta ne filtrira. */
    private static final UUID SOME_OTHER_CLIENT = UUID.randomUUID();

    private Category category;
    private Municipality municipality;
    private User tasker;

    @BeforeEach
    void setUp() {
        // Vlastiti sifrarnik, da se seed podaci ne mutiraju
        category = new Category();
        category.setName("Test kategorija " + UUID.randomUUID());
        category.setActive(true);
        category = categoryRepository.saveAndFlush(category);

        municipality = new Municipality();
        municipality.setName("Test opstina " + UUID.randomUUID());
        municipality = municipalityRepository.saveAndFlush(municipality);

        tasker = new User();
        tasker.setEmail("matching." + UUID.randomUUID() + "@test.ba");
        tasker.setPasswordHash("not-a-real-hash");
        tasker.setFirstName("Mirza");
        tasker.setLastName("Tasker");
        tasker.setAccountStatus(AccountStatus.ACTIVE);
        tasker = userRepository.saveAndFlush(tasker);

        TaskerProfile profile = new TaskerProfile();
        profile.setUser(tasker);
        profile.setCompletedJobsCount(0);
        profile.getCategories().add(category);
        profile.getMunicipalities().add(municipality);
        taskerProfileRepository.saveAndFlush(profile);
    }

    @AfterEach
    void tearDown() {
        taskerProfileRepository.deleteAll();
        userRepository.deleteAll();
        categoryRepository.delete(category);
        municipalityRepository.delete(municipality);
    }

    @Test
    @DisplayName("A tasker covering both the category and the municipality is a notification target")
    void findNotificationTargets_returnsTasker_whenCoverageMatches() {
        // Act
        List<TaskerNotificationTarget> targets = taskerProfileRepository
                .findNotificationTargets(category.getId(), municipality.getId(), SOME_OTHER_CLIENT);

        // Assert - projekcija nosi tacno ono sto notifikacija treba
        assertThat(targets).hasSize(1);
        assertThat(targets.getFirst().userId()).isEqualTo(tasker.getId());
        assertThat(targets.getFirst().email()).isEqualTo(tasker.getEmail());
        assertThat(targets.getFirst().fullName()).isEqualTo("Mirza Tasker");
    }

    @Test
    @DisplayName("Coverage of only one of the two dimensions is not a match")
    void findNotificationTargets_returnsEmpty_whenOnlyMunicipalityMatches() {
        // Arrange - druga kategorija koju tasker ne pokriva
        Category otherCategory = new Category();
        otherCategory.setName("Druga " + UUID.randomUUID());
        otherCategory.setActive(true);
        otherCategory = categoryRepository.saveAndFlush(otherCategory);

        // Act
        List<TaskerNotificationTarget> targets = taskerProfileRepository
                .findNotificationTargets(otherCategory.getId(), municipality.getId(), SOME_OTHER_CLIENT);

        // Assert
        assertThat(targets).isEmpty();

        categoryRepository.delete(otherCategory);
    }

    @Test
    @DisplayName("A deactivated category yields no targets, even though the coverage row remains")
    void findNotificationTargets_returnsEmpty_whenCategoryIsDeactivated() {
        // Arrange - admin gasi kategoriju; red u tasker_categories ostaje
        category.setActive(false);
        categoryRepository.saveAndFlush(category);

        // Act
        List<TaskerNotificationTarget> targets = taskerProfileRepository
                .findNotificationTargets(category.getId(), municipality.getId(), SOME_OTHER_CLIENT);

        // Assert - bez filtera u upitu notifikacije bi isle za ugasenu kategoriju
        assertThat(targets).isEmpty();
    }

    @Test
    @DisplayName("Reactivating a category restores the previous coverage on its own")
    void findNotificationTargets_returnsTaskerAgain_whenCategoryIsReactivated() {
        // Arrange
        category.setActive(false);
        categoryRepository.saveAndFlush(category);
        assertThat(taskerProfileRepository
                .findNotificationTargets(category.getId(), municipality.getId(), SOME_OTHER_CLIENT)).isEmpty();

        // Act - admin se pokajao
        category.setActive(true);
        categoryRepository.saveAndFlush(category);

        // Assert - ovo je razlog zasto se filtrira u upitu, a ne cisti spojna
        // tabela: izbor taskera se nije izgubio, pa ga ne mora ponovo praviti.
        assertThat(taskerProfileRepository
                .findNotificationTargets(category.getId(), municipality.getId(), SOME_OTHER_CLIENT))
                .hasSize(1);
    }

    @Test
    @DisplayName("A tasker is not notified about a task they posted themselves")
    void findNotificationTargets_excludesTheClient_whenTheyAreAlsoTheTasker() {
        // Arrange - jedan nalog moze imati i CLIENT i TASKER rolu, pa vlasnik
        // oglasa moze istovremeno biti tasker koji pokriva tu kategoriju i opstinu

        // Act - oglas je objavio bas taj korisnik
        List<TaskerNotificationTarget> targets = taskerProfileRepository
                .findNotificationTargets(category.getId(), municipality.getId(), tasker.getId());

        // Assert
        assertThat(targets)
                .as("niko ne treba notifikaciju o vlastitom oglasu")
                .isEmpty();
    }
}
