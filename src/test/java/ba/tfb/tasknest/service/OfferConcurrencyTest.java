package ba.tfb.tasknest.service;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.offer.CreateOfferRequest;
import ba.tfb.tasknest.entity.*;
import ba.tfb.tasknest.entity.enums.AccountStatus;
import ba.tfb.tasknest.entity.enums.OfferStatus;
import ba.tfb.tasknest.entity.enums.RoleName;
import ba.tfb.tasknest.entity.enums.TaskStatus;
import ba.tfb.tasknest.exception.BusinessRuleException;
import ba.tfb.tasknest.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves that concurrent access to the same task cannot produce
 * an inconsistent state. These tests deliberately avoid @Transactional
 * so that each thread runs in its own real transaction.
 */
class OfferConcurrencyTest extends AbstractIntegrationTest {

    @Autowired private OfferService offerService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private OfferRepository offerRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MunicipalityRepository municipalityRepository;
    @Autowired private ConversationRepository conversationRepository;

    private User client;
    private User taskerOne;
    private User taskerTwo;
    private User taskerThree;
    private Task task;

    @BeforeEach
    void setUp() {
        client = createUser("client@test.ba", RoleName.CLIENT);
        taskerOne = createUser("tasker1@test.ba", RoleName.TASKER);
        taskerTwo = createUser("tasker2@test.ba", RoleName.TASKER);
        taskerThree = createUser("tasker3@test.ba", RoleName.TASKER);

        Category category = categoryRepository.findAll().getFirst();
        Municipality municipality = municipalityRepository.findAll().getFirst();

        task = new Task();
        task.setClient(client);
        task.setCategory(category);
        task.setMunicipality(municipality);
        task.setTitle("Replace the kitchen sink pipe");
        task.setDescription("Leaking pipe under the sink");
        task.setBudget(new BigDecimal("80.00"));
        task.setStatus(TaskStatus.PUBLISHED);
        task.setPublishedAt(LocalDateTime.now());
        task.setExpiresAt(LocalDateTime.now().plusDays(30));
        task = taskRepository.save(task);
    }

    @AfterEach
    void tearDown() {
        conversationRepository.deleteAll();
        taskRepository.findAll().forEach(t -> {
            t.setAcceptedOffer(null);
            taskRepository.save(t);
        });
        offerRepository.deleteAll();
        taskRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Two clients accepting different offers at the same time: exactly one wins")
    void concurrentAcceptOffersLeaveExactlyOneAccepted() throws Exception {
        Offer offerOne = createOffer(taskerOne, "70.00");
        Offer offerTwo = createOffer(taskerTwo, "75.00");

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        Queue<Throwable> caught = runInParallel(
                () -> offerService.acceptOffer(offerOne.getId(), client.getId()),
                () -> offerService.acceptOffer(offerTwo.getId(), client.getId()),
                successes,
                failures
        );

        assertNoDeadlock(caught);

        assertEquals(1, successes.get(), "Exactly one accept must succeed");
        assertEquals(1, failures.get(), "The losing accept must fail");

        Throwable loser = caught.peek();
        assertNotNull(loser, "The losing accept must have recorded an exception");
        assertInstanceOf(
                ObjectOptimisticLockingFailureException.class,
                loser,
                describe("The losing accept must fail on the task version check", loser)
        );

        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(TaskStatus.ASSIGNED, reloaded.getStatus());
        assertNotNull(reloaded.getAcceptedOffer());

        List<Offer> accepted = offerRepository.findByTaskAndStatus(reloaded, OfferStatus.ACCEPTED);
        assertEquals(1, accepted.size(), "Only one offer may end up accepted");

        List<Offer> pending = offerRepository.findByTaskAndStatus(reloaded, OfferStatus.PENDING);
        assertTrue(pending.isEmpty(), "No offer may remain pending on an assigned task");
    }

    @Test
    @DisplayName("Accepting while another tasker submits: no pending offer survives on an assigned task")
    void concurrentSubmitAndAcceptLeaveNoDanglingOffer() throws Exception {
        Offer existing = createOffer(taskerOne, "70.00");

        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();

        Queue<Throwable> caught = runInParallel(
                () -> offerService.acceptOffer(existing.getId(), client.getId()),
                () -> offerService.submitOffer(
                        task.getId(),
                        taskerThree.getId(),
                        new CreateOfferRequest(new BigDecimal("60.00"), "I can do it today")),
                successes,
                failures
        );

        assertNoDeadlock(caught);

        // Ovdje su oba ishoda ispravna, pa se ne tvrdi da MORA biti pada. Ako ga je
        // bilo, dozvoljena su tacno dva razloga: submit je pao na provjeri verzije
        // taska, ili je stigao nakon accepta i regularno vidio da task vise nije
        // PUBLISHED. Sve ostalo je greska.
        for (Throwable failure : caught) {
            assertTrue(
                    failure instanceof ObjectOptimisticLockingFailureException
                            || failure instanceof BusinessRuleException,
                    describe("Only a version conflict or a 'task no longer published' "
                            + "rejection may fail here", failure)
            );
        }

        Task reloaded = taskRepository.findById(task.getId()).orElseThrow();
        assertEquals(TaskStatus.ASSIGNED, reloaded.getStatus());

        List<Offer> pending = offerRepository.findByTaskAndStatus(reloaded, OfferStatus.PENDING);
        assertTrue(pending.isEmpty(),
                "A pending offer must never survive on an assigned task, found: " + pending.size());
    }

    // ---------- helpers ----------

    /** Postgres SQLSTATE za "deadlock detected". */
    private static final String DEADLOCK_SQL_STATE = "40P01";

    /** Koliko duboko se ide niz lanac uzroka pri pretrazi. */
    private static final int MAX_CAUSE_DEPTH = 20;

    private Queue<Throwable> runInParallel(Runnable first,
                                           Runnable second,
                                           AtomicInteger successes,
                                           AtomicInteger failures) throws Exception {

        CountDownLatch startSignal = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(2);
        Queue<Throwable> caught = new ConcurrentLinkedQueue<>();
        AtomicReference<Throwable> unexpected = new AtomicReference<>();

        ExecutorService pool = Executors.newFixedThreadPool(2);

        for (Runnable action : List.of(first, second)) {
            pool.submit(() -> {
                try {
                    startSignal.await();
                    action.run();
                    successes.incrementAndGet();
                } catch (Exception e) {
                    caught.add(e);
                    failures.incrementAndGet();
                } catch (Throwable t) {
                    unexpected.set(t);
                } finally {
                    finished.countDown();
                }
            });
        }

        startSignal.countDown();
        assertTrue(finished.await(15, TimeUnit.SECONDS), "Threads did not finish in time");
        pool.shutdown();

        if (unexpected.get() != null) {
            fail("Unexpected error: " + unexpected.get());
        }

        return caught;
    }

    /**
     * Deadlock znaci da su transakcije opet zakljucale redove ukrstenim redoslijedom.
     * Provjerava se i Springov tip i SQLSTATE i tekst poruke, jer prevod izuzetka
     * ovisi o verziji drajvera i Springovom translatoru.
     */
    private void assertNoDeadlock(Collection<Throwable> caught) {
        for (Throwable failure : caught) {
            assertFalse(
                    isDeadlock(failure),
                    describe("DEADLOCK JE PONOVO ISKRSNUO - transakcije zakljucavaju "
                            + "redove ukrstenim redoslijedom. Provjeri da se task upisuje "
                            + "i flushuje PRIJE ijedne ponude, u acceptOffer i cancelTask",
                            failure)
            );
        }
    }

    private static boolean isDeadlock(Throwable throwable) {
        Throwable current = throwable;

        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof CannotAcquireLockException) {
                return true;
            }
            if (current instanceof SQLException sqlException
                    && DEADLOCK_SQL_STATE.equals(sqlException.getSQLState())) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("deadlock detected")) {
                return true;
            }

            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }

        return false;
    }

    /** Poruka koja pokazuje stvarni tip i uzrok, umjesto golog "expected true but was false". */
    private static String describe(String expectation, Throwable throwable) {
        Throwable root = rootCause(throwable);

        String detail = expectation
                + System.lineSeparator() + "  dobijeno: " + throwable.getClass().getName()
                + ": " + throwable.getMessage();

        if (root != throwable) {
            detail += System.lineSeparator() + "  uzrok:    " + root.getClass().getName()
                    + ": " + root.getMessage();
        }

        return detail;
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;

        for (int depth = 0; depth < MAX_CAUSE_DEPTH; depth++) {
            Throwable cause = current.getCause();
            if (cause == null || cause == current) {
                break;
            }
            current = cause;
        }

        return current;
    }

    private User createUser(String email, RoleName roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow();

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash("not-a-real-hash");
        user.setFirstName("Test");
        user.setLastName(roleName.name());
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.getRoles().add(role);

        return userRepository.save(user);
    }

    private Offer createOffer(User tasker, String price) {
        Offer offer = new Offer();
        offer.setTask(task);
        offer.setTasker(tasker);
        offer.setPrice(new BigDecimal(price));
        offer.setStatus(OfferStatus.PENDING);
        return offerRepository.save(offer);
    }
}