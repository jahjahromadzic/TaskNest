package ba.tfb.tasknest.bootstrap;

import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.entity.Conversation;
import ba.tfb.tasknest.entity.Message;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.entity.Offer;
import ba.tfb.tasknest.entity.Review;
import ba.tfb.tasknest.entity.Role;
import ba.tfb.tasknest.entity.Task;
import ba.tfb.tasknest.entity.TaskerProfile;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.AccountStatus;
import ba.tfb.tasknest.entity.enums.ConversationStatus;
import ba.tfb.tasknest.entity.enums.OfferStatus;
import ba.tfb.tasknest.entity.enums.RoleName;
import ba.tfb.tasknest.entity.enums.TaskStatus;
import ba.tfb.tasknest.repository.CategoryRepository;
import ba.tfb.tasknest.repository.ConversationRepository;
import ba.tfb.tasknest.repository.MessageRepository;
import ba.tfb.tasknest.repository.MunicipalityRepository;
import ba.tfb.tasknest.repository.OfferRepository;
import ba.tfb.tasknest.repository.ReviewRepository;
import ba.tfb.tasknest.repository.RoleRepository;
import ba.tfb.tasknest.repository.TaskRepository;
import ba.tfb.tasknest.repository.TaskerProfileRepository;
import ba.tfb.tasknest.repository.UserRepository;
import ba.tfb.tasknest.service.TaskService;
import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.demo-data.enabled", havingValue = "true")
@Slf4j
public class DemoDataSeeder {

    public static final String EMAIL_DOMAIN = "@demo.tasknest.ba";

    private static final UUID PLUMBING = UUID.fromString("a1000000-0000-0000-0000-000000000001");
    private static final UUID ELECTRICAL = UUID.fromString("a1000000-0000-0000-0000-000000000002");
    private static final UUID MOVING = UUID.fromString("a1000000-0000-0000-0000-000000000003");
    private static final UUID FURNITURE = UUID.fromString("a1000000-0000-0000-0000-000000000004");
    private static final UUID CLEANING = UUID.fromString("a1000000-0000-0000-0000-000000000005");
    private static final UUID PAINTING = UUID.fromString("a1000000-0000-0000-0000-000000000006");
    private static final UUID TILING = UUID.fromString("a1000000-0000-0000-0000-000000000007");
    private static final UUID CARPENTRY = UUID.fromString("a1000000-0000-0000-0000-000000000008");
    private static final UUID AIR_CONDITIONING = UUID.fromString("a1000000-0000-0000-0000-000000000009");
    private static final UUID HEATING = UUID.fromString("a1000000-0000-0000-0000-00000000000a");
    private static final UUID APPLIANCES = UUID.fromString("a1000000-0000-0000-0000-00000000000b");
    private static final UUID LOCKSMITH = UUID.fromString("a1000000-0000-0000-0000-00000000000c");
    private static final UUID GARDENING = UUID.fromString("a1000000-0000-0000-0000-00000000000d");
    private static final UUID COMPUTERS = UUID.fromString("a1000000-0000-0000-0000-00000000000e");

    private static final UUID CENTAR = UUID.fromString("b2000000-0000-0000-0000-000000000001");
    private static final UUID NOVO_SARAJEVO = UUID.fromString("b2000000-0000-0000-0000-000000000002");
    private static final UUID NOVI_GRAD = UUID.fromString("b2000000-0000-0000-0000-000000000003");
    private static final UUID STARI_GRAD = UUID.fromString("b2000000-0000-0000-0000-000000000004");
    private static final UUID ILIDZA = UUID.fromString("b2000000-0000-0000-0000-000000000005");
    private static final UUID VOGOSCA = UUID.fromString("b2000000-0000-0000-0000-000000000006");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CategoryRepository categoryRepository;
    private final MunicipalityRepository municipalityRepository;
    private final TaskerProfileRepository taskerProfileRepository;
    private final TaskRepository taskRepository;
    private final OfferRepository offerRepository;
    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final ReviewRepository reviewRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;
    private final Clock clock;
    private final String password;

    private final Map<User, TaskerProfile> profiles = new HashMap<>();
    private final Map<User, List<Integer>> ratings = new HashMap<>();
    private final List<Runnable> backdating = new ArrayList<>();
    private LocalDateTime now;
    private String passwordHash;

    public DemoDataSeeder(UserRepository userRepository,
                          RoleRepository roleRepository,
                          CategoryRepository categoryRepository,
                          MunicipalityRepository municipalityRepository,
                          TaskerProfileRepository taskerProfileRepository,
                          TaskRepository taskRepository,
                          OfferRepository offerRepository,
                          ConversationRepository conversationRepository,
                          MessageRepository messageRepository,
                          ReviewRepository reviewRepository,
                          PasswordEncoder passwordEncoder,
                          EntityManager entityManager,
                          Clock clock,
                          @Value("${app.demo-data.password}") String password) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.categoryRepository = categoryRepository;
        this.municipalityRepository = municipalityRepository;
        this.taskerProfileRepository = taskerProfileRepository;
        this.taskRepository = taskRepository;
        this.offerRepository = offerRepository;
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.reviewRepository = reviewRepository;
        this.passwordEncoder = passwordEncoder;
        this.entityManager = entityManager;
        this.clock = clock;
        this.password = password;

        if (password == null || password.isBlank()) {
            throw new IllegalStateException("app.demo-data.password must be set when demo data is enabled");
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void seed() {
        if (userRepository.existsByEmail("amra" + EMAIL_DOMAIN)) {
            log.info("Demo data is already in the database");
            return;
        }

        now = LocalDateTime.now(clock);
        passwordHash = passwordEncoder.encode(password);
        profiles.clear();
        ratings.clear();
        backdating.clear();

        User amra = user("amra", "Amra", "Hodžić", "+387 61 111 222", RoleName.CLIENT);
        User emina = user("emina", "Emina", "Begić", "+387 62 333 444", RoleName.CLIENT);
        User haris = user("haris", "Haris", "Mehić", null, RoleName.CLIENT);
        user("lejla", "Lejla", "Mujić", null, RoleName.CLIENT, RoleName.ADMIN);

        User emir = tasker("emir", "Emir", "Kovačević",
                "Plumber and electrician with 12 years of experience",
                "I fix water and electrical faults all over Sarajevo. I bring my own tools and spare parts, "
                        + "and every job comes with a six-month guarantee.",
                List.of(PLUMBING, ELECTRICAL, HEATING, APPLIANCES), List.of(CENTAR, STARI_GRAD, NOVO_SARAJEVO), true);
        User selma = tasker("selma", "Selma", "Karić",
                "Stress-free cleaning and painting",
                "I leave every flat cleaner than I found it. When painting I cover everything with sheets, "
                        + "so there are no stains on the floor or the furniture.",
                List.of(CLEANING, PAINTING, GARDENING), List.of(NOVI_GRAD, ILIDZA, NOVO_SARAJEVO, CENTAR), true);
        User adnan = tasker("adnan", "Adnan", "Delić",
                "Moving and furniture assembly, van included",
                "I move flats and offices and assemble IKEA and other furniture. A 12 m³ van and a helper "
                        + "for heavy items are included in the price.",
                List.of(MOVING, FURNITURE, CARPENTRY, LOCKSMITH), List.of(CENTAR, NOVO_SARAJEVO, NOVI_GRAD, STARI_GRAD, ILIDZA, VOGOSCA), false);
        User tarik = tasker("tarik", "Tarik", "Hasanović",
                "Young electrician and painter",
                "I finished electrical school and take on small electrical and painting jobs. Quick and affordable.",
                List.of(ELECTRICAL, PAINTING, AIR_CONDITIONING, COMPUTERS), List.of(VOGOSCA, NOVI_GRAD, CENTAR), false);

        seedOpenTasks(amra, emina, haris, emir, selma, adnan, tarik);
        seedAmraStory(amra, emir, selma, adnan);
        seedFinishedJobs(emina, haris, emir, selma, adnan);

        finishProfiles();
        entityManager.flush();
        backdating.forEach(Runnable::run);

        log.info("Demo data created: 8 users with the password from app.demo-data.password, "
                + "log in as amra{} (client), emir{} (tasker) or lejla{} (admin)", EMAIL_DOMAIN, EMAIL_DOMAIN, EMAIL_DOMAIN);
    }

    private void seedOpenTasks(User amra, User emina, User haris,
                               User emir, User selma, User adnan, User tarik) {
        Task sink = published(amra, PLUMBING, CENTAR, "Leaking bathroom tap",
                "The washbasin tap drips even when it is fully closed. The washer or the whole tap head "
                        + "probably needs replacing. I have a new tap if needed.", 60, 20);
        offer(sink, emir, 55, "I can come tomorrow after 4 pm, replacing the washer takes half an hour.", OfferStatus.PENDING, 14);

        Task boiler = published(emina, PLUMBING, NOVO_SARAJEVO, "Replace an 80-litre water heater",
                "The old water heater has started leaking. The new one is already in the flat. The old one needs to come "
                        + "off, the new one needs mounting and the old one taken to the tip.", 250, 3 * 24);
        offer(boiler, emir, 230, "I have done dozens of these. Taking away the old heater is included.", OfferStatus.PENDING, 60);

        published(haris, ELECTRICAL, STARI_GRAD, "Add a socket and replace switches in the living room",
                "I need one double socket next to the TV and two old switches replaced. The walls are brick.",
                80, 2 * 24);

        Task hallway = published(emina, ELECTRICAL, VOGOSCA, "Install lighting in the hallway",
                "The hallway is 6 metres long and has a single bulb. I would like three recessed LED lights. Open to offers.",
                null, 6 * 24);
        offer(hallway, tarik, 70, "I can fit three LED lights with a switch at both ends of the hallway.", OfferStatus.PENDING, 5 * 24);

        Task move = published(haris, MOVING, NOVI_GRAD, "Move a two-bedroom flat to Ilidža",
                "We are moving from Alipašino Polje to Ilidža, 4th floor with a lift. Furniture, about 20 boxes, "
                        + "a washing machine and a fridge need to go.", 400, 4 * 24);
        offer(move, adnan, 380, "A van and two movers, done in one day. Taking the wardrobes apart is included.",
                OfferStatus.PENDING, 3 * 24);

        published(amra, MOVING, CENTAR, "Transport a washing machine and a fridge",
                "I bought second-hand appliances in Novo Sarajevo. They need to go to Centar and up to the 2nd floor without a lift.",
                90, 8 * 24);

        Task wardrobe = published(emina, FURNITURE, ILIDZA, "Assemble an IKEA PAX wardrobe",
                "PAX wardrobe, 200 x 236 cm with sliding doors, all boxes are in the room. It also needs fixing to the wall.",
                120, 5);
        offer(wardrobe, adnan, 110, "I have built many PAX wardrobes and have the tools to fix it to the wall.", OfferStatus.PENDING, 2);

        published(haris, FURNITURE, NOVO_SARAJEVO, "Fit a kitchen with eight units",
                "A new kitchen from the showroom, eight units plus a worktop. Openings for the sink and the hob need cutting.",
                350, 10 * 24);

        Task cleaning = published(amra, CLEANING, NOVI_GRAD, "Deep clean a 65 m² flat after renovation",
                "Painting and new laminate left a lot of dust. Windows, floors, the bathroom and the kitchen need cleaning.",
                150, 5 * 24);
        offer(cleaning, selma, 140, "I bring a professional vacuum cleaner and all the products.", OfferStatus.PENDING, 4 * 24);

        published(emina, CLEANING, STARI_GRAD, "Clean windows and shutters",
                "Six windows and four outside shutters in a first-floor flat.", 70, 12 * 24);

        Task painting = published(haris, PAINTING, CENTAR, "Paint a two-bedroom flat",
                "A 55 m² flat with walls in good condition. I buy the paint, I only need the work and the furniture covered.",
                600, 7 * 24);
        offer(painting, selma, 550, "Two coats, everything covered with sheets. Done in three days.", OfferStatus.PENDING, 6 * 24);
        offer(painting, tarik, 580, "I can start this weekend.", OfferStatus.PENDING, 5 * 24);

        published(amra, PAINTING, VOGOSCA, "Plaster and paint one room",
                "The children's room has cracks on two walls. They need filling and the room painted light blue.",
                null, 15 * 24);

        published(haris, PLUMBING, ILIDZA, "Blocked kitchen drain",
                "Water drains slowly from the sink and shop-bought products did not help.", 50, 3);

        published(amra, ELECTRICAL, NOVI_GRAD, "Replace fuses and check the wiring",
                "The fuse often trips when the washing machine runs. The wiring needs checking and the old fuses replacing.",
                100, 20 * 24);

        published(haris, CLEANING, CENTAR, "Weekly office cleaning",
                "A 40 m² office in the city centre, cleaning on Fridays after 5 pm.", 200, 26 * 24);

        published(emina, FURNITURE, STARI_GRAD, "Mount a TV on the wall",
                "A 55-inch TV, the bracket is already bought. The wall is concrete.", 40, 28 * 24);

        published(emina, PAINTING, ILIDZA, "Paint a wooden fence",
                "The fence around the garden is about 30 metres long. It needs cleaning and a coat of wood stain.", 180, 9 * 24);

        published(amra, MOVING, VOGOSCA, "Clear out old furniture",
                "An old sofa, two wardrobes and a bed need carrying out of the house and taking to the tip.", null, 11 * 24);

        published(haris, TILING, NOVO_SARAJEVO, "Tile a bathroom wall",
                "A 5 m² bathroom, tiles and adhesive are bought. The old tiles above the bath need removing first.",
                450, 30);

        Task airConditioner = published(emina, AIR_CONDITIONING, CENTAR, "Service and clean air conditioners before summer",
                "Two inverter units, one in the living room and one in the bedroom. Not serviced for two years.",
                60, 2 * 24);
        offer(airConditioner, tarik, 55, "I clean the indoor and outdoor units and check the refrigerant.", OfferStatus.PENDING, 30);

        Task washingMachine = published(haris, APPLIANCES, NOVI_GRAD, "Washing machine does not drain",
                "Water stays in the drum after washing and the display shows E21. The machine is five years old.", null, 26);
        offer(washingMachine, emir, 50, "Usually it is a blocked pump or filter, fixed the same day.", OfferStatus.PENDING, 20);

        published(emina, GARDENING, ILIDZA, "Mow the lawn and trim the hedge",
                "A garden of about 300 m² and a 20-metre hedge. The cuttings need taking away.", 80, 4 * 24);

        published(amra, LOCKSMITH, STARI_GRAD, "Replace the lock cylinder on the front door",
                "The key is hard to turn. I want a new security cylinder with five keys.", 50, 9);

        published(haris, COMPUTERS, VOGOSCA, "Set up a Wi-Fi router and a printer",
                "A new router from the provider, but the signal does not reach the bedroom. The printer needs connecting too.", 40, 6 * 24);
    }

    private void seedAmraStory(User amra, User emir, User selma, User adnan) {
        Task siphon = published(amra, PLUMBING, CENTAR, "Replace the trap and valve under the sink",
                "The trap under the sink is cracked and the hot water valve does not close all the way.", 90, 6 * 24);
        Offer siphonOffer = offer(siphon, emir, 85, "I have both parts in stock and can come on Thursday.",
                OfferStatus.ACCEPTED, 5 * 24);
        offer(siphon, adnan, 95, "I can help too, I will bring my tools.", OfferStatus.REJECTED, 5 * 24);
        assign(siphon, siphonOffer, 2 * 24);
        Conversation chat = conversationRepository.findByOffer(siphonOffer).orElseThrow();
        message(chat, amra, "Hi Emir, I accepted your offer. Does Thursday at 5 pm work for you?", 47, true);
        message(chat, emir, "Hi, that works. Could you send me the exact address and floor?", 46, true);
        message(chat, amra, "Zmaja od Bosne 12, third floor, flat 7. The intercom is broken, so please call me.", 45, true);
        message(chat, emir, "Agreed, see you on Thursday. I will bring a new cold water valve too, just in case.", 3, false);

        Task kitchen = published(amra, PAINTING, CENTAR, "Paint the kitchen",
                "A 12 m² kitchen with a yellowed ceiling above the cooker. It needs washing and painting with steam-resistant paint.",
                200, 9 * 24);
        Offer kitchenOffer = offer(kitchen, selma, 190, "I use washable kitchen paint that dries in two hours.",
                OfferStatus.ACCEPTED, 8 * 24);
        assign(kitchen, kitchenOffer, 5 * 24);
        kitchen.setStatus(TaskStatus.IN_PROGRESS);
        kitchen.setStartedAt(now.minusHours(20));

        Task bed = published(amra, FURNITURE, CENTAR, "Assemble a bed and a chest of drawers",
                "A new double bed and a six-drawer chest, all still in boxes.", 100, 20 * 24);
        Offer bedOffer = offer(bed, adnan, 100, "Done in two hours, and I take the cardboard away.", OfferStatus.ACCEPTED, 19 * 24);
        close(bed, bedOffer, 18 * 24, 16 * 24, 15 * 24);
        review(bed, amra, adnan, 5, "Fast, precise and tidy. He even took all the cardboard away. Recommended!", 14 * 24);
        review(bed, adnan, amra, 5, "Everything was ready when I arrived. A pleasure to work with.", 14 * 24);

        Task draft = new Task();
        draft.setClient(amra);
        draft.setCategory(category(FURNITURE));
        draft.setMunicipality(municipality(CENTAR));
        draft.setTitle("Lay laminate flooring in the bedroom");
        draft.setDescription("A 14 m² room, the laminate is not bought yet.");
        draft.setBudget(new BigDecimal("250"));
        taskRepository.save(draft);
    }

    private void seedFinishedJobs(User emina, User haris, User emir, User selma, User adnan) {
        Task boilerRepair = published(emina, PLUMBING, NOVO_SARAJEVO, "Repair a water heater that does not heat",
                "A 50-litre water heater with the light on but no hot water.", 70, 25 * 24);
        Offer boilerOffer = offer(boilerRepair, emir, 70, "Probably the heating element, and I have one with me.", OfferStatus.ACCEPTED, 24 * 24);
        close(boilerRepair, boilerOffer, 23 * 24, 22 * 24, 22 * 24);
        review(boilerRepair, emina, emir, 4, "Good work and a fair price, but he was half an hour late.", 21 * 24);

        Task carpets = published(haris, CLEANING, NOVI_GRAD, "Deep clean carpets",
                "Three carpets in the living room and the bedroom.", 90, 18 * 24);
        Offer carpetOffer = offer(carpets, selma, 85, "I have a deep-cleaning machine and the carpets are dry by the evening.",
                OfferStatus.ACCEPTED, 17 * 24);
        close(carpets, carpetOffer, 16 * 24, 15 * 24, 15 * 24);
        review(carpets, haris, selma, 5, "The carpets look brand new. Highly recommended!", 14 * 24);

        Task studio = published(emina, MOVING, ILIDZA, "Move a studio flat",
                "A 30 m² studio with little furniture and about fifteen boxes.", 150, 22 * 24);
        Offer studioOffer = offer(studio, adnan, 150, "Done in half a day.", OfferStatus.ACCEPTED, 21 * 24);
        close(studio, studioOffer, 20 * 24, 19 * 24, 19 * 24);
        review(studio, emina, adnan, 4, "Everything arrived in one piece, only one box was squashed.", 18 * 24);
    }

    private User user(String login, String firstName, String lastName, String phone, RoleName... roleNames) {
        User user = new User();
        user.setEmail(login + EMAIL_DOMAIN);
        user.setPasswordHash(passwordHash);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPhone(phone);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setEmailVerified(true);
        Arrays.stream(roleNames).map(this::role).forEach(user.getRoles()::add);
        return userRepository.save(user);
    }

    private User tasker(String login, String firstName, String lastName, String headline, String bio,
                        List<UUID> categoryIds, List<UUID> municipalityIds, boolean verified) {
        User tasker = user(login, firstName, lastName, "+387 65 000 " + (100 + profiles.size()),
                RoleName.CLIENT, RoleName.TASKER);

        TaskerProfile profile = new TaskerProfile();
        profile.setUser(tasker);
        profile.setHeadline(headline);
        profile.setBio(bio);
        profile.setVerified(verified);
        profile.getCategories().addAll(categoryRepository.findAllById(categoryIds));
        profile.getMunicipalities().addAll(municipalityRepository.findAllById(municipalityIds));
        profiles.put(tasker, taskerProfileRepository.save(profile));
        return tasker;
    }

    private Task published(User client, UUID categoryId, UUID municipalityId, String title, String description,
                           Integer budget, int hoursAgo) {
        LocalDateTime publishedAt = now.minusHours(hoursAgo);

        Task task = new Task();
        task.setClient(client);
        task.setCategory(category(categoryId));
        task.setMunicipality(municipality(municipalityId));
        task.setTitle(title);
        task.setDescription(description);
        task.setBudget(budget == null ? null : new BigDecimal(budget));
        task.setStatus(TaskStatus.PUBLISHED);
        task.setPublishedAt(publishedAt);
        task.setExpiresAt(publishedAt.plusDays(TaskService.PUBLICATION_VALIDITY_DAYS));
        Task saved = taskRepository.save(task);
        backdate("tasks", saved.getId(), publishedAt.minusMinutes(30));
        return saved;
    }

    private Offer offer(Task task, User tasker, int price, String message, OfferStatus status, int hoursAgo) {
        Offer offer = new Offer();
        offer.setTask(task);
        offer.setTasker(tasker);
        offer.setPrice(new BigDecimal(price));
        offer.setMessage(message);
        offer.setStatus(status);
        Offer saved = offerRepository.save(offer);
        backdate("offers", saved.getId(), now.minusHours(hoursAgo));

        Conversation conversation = new Conversation();
        conversation.setOffer(saved);
        conversation.setStatus(status == OfferStatus.REJECTED ? ConversationStatus.ARCHIVED : ConversationStatus.OPEN);
        Conversation savedConversation = conversationRepository.save(conversation);
        backdate("conversations", savedConversation.getId(), now.minusHours(hoursAgo));
        return saved;
    }

    private void assign(Task task, Offer accepted, int hoursAgo) {
        task.setStatus(TaskStatus.ASSIGNED);
        task.setAcceptedOffer(accepted);
        task.setAssignedAt(now.minusHours(hoursAgo));
    }

    private void close(Task task, Offer accepted, int assignedHoursAgo, int startedHoursAgo, int completedHoursAgo) {
        assign(task, accepted, assignedHoursAgo);
        task.setStartedAt(now.minusHours(startedHoursAgo));
        task.setCompletedAt(now.minusHours(completedHoursAgo));
        task.setStatus(TaskStatus.CLOSED);
        conversationRepository.findByOffer(accepted)
                .ifPresent(conversation -> conversation.setStatus(ConversationStatus.ARCHIVED));
        TaskerProfile profile = profiles.get(accepted.getTasker());
        profile.setCompletedJobsCount(profile.getCompletedJobsCount() + 1);
    }

    private void message(Conversation conversation, User sender, String content, int hoursAgo, boolean read) {
        LocalDateTime sentAt = now.minusHours(hoursAgo);

        Message message = new Message();
        message.setConversation(conversation);
        message.setSender(sender);
        message.setContent(content);
        message.setReadAt(read ? sentAt.plusMinutes(10) : null);
        Message saved = messageRepository.save(message);
        backdate("messages", saved.getId(), sentAt);

        conversation.setLastMessageAt(sentAt);
    }

    private void review(Task task, User reviewer, User reviewee, int rating, String comment, int hoursAgo) {
        Review review = new Review();
        review.setTask(task);
        review.setReviewer(reviewer);
        review.setReviewee(reviewee);
        review.setRating(rating);
        review.setComment(comment);
        Review saved = reviewRepository.save(review);
        backdate("reviews", saved.getId(), now.minusHours(hoursAgo));
        ratings.computeIfAbsent(reviewee, key -> new ArrayList<>()).add(rating);
    }

    private void finishProfiles() {
        profiles.forEach((tasker, profile) -> {
            List<Integer> received = ratings.get(tasker);
            if (received != null) {
                double average = received.stream().mapToInt(Integer::intValue).average().orElseThrow();
                profile.setAverageRating(BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP));
            }
        });
    }

    private void backdate(String table, UUID id, LocalDateTime createdAt) {
        backdating.add(() -> entityManager
                .createNativeQuery("update " + table + " set created_at = :createdAt where id = :id")
                .setParameter("createdAt", createdAt)
                .setParameter("id", id)
                .executeUpdate());
    }

    private Role role(RoleName name) {
        return roleRepository.findByName(name)
                .orElseThrow(() -> new IllegalStateException(name + " role is missing"));
    }

    private Category category(UUID id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Seed category is missing: " + id));
    }

    private Municipality municipality(UUID id) {
        return municipalityRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Seed municipality is missing: " + id));
    }
}
