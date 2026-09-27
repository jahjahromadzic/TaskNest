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
                "Vodoinstalater i električar, 12 godina iskustva",
                "Radim kvarove na vodi i struji po cijelom Sarajevu. Dolazim s alatom i rezervnim dijelovima, "
                        + "a na svaki posao dajem garanciju od šest mjeseci.",
                List.of(PLUMBING, ELECTRICAL, HEATING, APPLIANCES), List.of(CENTAR, STARI_GRAD, NOVO_SARAJEVO), true);
        User selma = tasker("selma", "Selma", "Karić",
                "Čišćenje i krečenje bez stresa",
                "Stan ostavljam čišći nego što sam ga zatekla. Za krečenje donosim folije i sve pokrivam, "
                        + "pa poslije nema mrlja po podu i namještaju.",
                List.of(CLEANING, PAINTING, GARDENING), List.of(NOVI_GRAD, ILIDZA, NOVO_SARAJEVO, CENTAR), true);
        User adnan = tasker("adnan", "Adnan", "Delić",
                "Selidbe i montaža namještaja, imam kombi",
                "Selim stanove i kancelarije, sklapam IKEA i drugi namještaj. Kombi od 12 m³ i pomoćnik "
                        + "za teže stvari su uključeni u cijenu.",
                List.of(MOVING, FURNITURE, CARPENTRY, LOCKSMITH), List.of(CENTAR, NOVO_SARAJEVO, NOVI_GRAD, STARI_GRAD, ILIDZA, VOGOSCA), false);
        User tarik = tasker("tarik", "Tarik", "Hasanović",
                "Mladi električar i moler",
                "Završio sam elektrotehničku školu i radim sitne elektro i molerske poslove. Brz sam i povoljan.",
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
        Task sink = published(amra, PLUMBING, CENTAR, "Curi slavina u kupatilu",
                "Slavina na umivaoniku kaplje i kad je skroz zatvorena. Vjerovatno treba zamijeniti dihtung ili cijelu "
                        + "glavu slavine. Imam novu slavinu ako bude trebalo.", 60, 20);
        offer(sink, emir, 55, "Mogu doći sutra poslije 16h, zamjena dihtunga traje pola sata.", OfferStatus.PENDING, 14);

        Task boiler = published(emina, PLUMBING, NOVO_SARAJEVO, "Zamjena bojlera od 80 litara",
                "Stari bojler je počeo curiti. Novi je kupljen i stoji u stanu, treba skinuti stari, montirati novi "
                        + "i odvesti stari na otpad.", 250, 3 * 24);
        offer(boiler, emir, 230, "Radio sam desetine ovakvih zamjena. Odvoz starog bojlera je uključen.", OfferStatus.PENDING, 60);

        published(haris, ELECTRICAL, STARI_GRAD, "Ugradnja nove utičnice i prekidača u dnevnom boravku",
                "Treba dodati jednu duplu utičnicu pored TV-a i zamijeniti dva stara prekidača. Zidovi su od cigle.",
                80, 2 * 24);

        Task hallway = published(emina, ELECTRICAL, VOGOSCA, "Postavljanje rasvjete u hodniku",
                "Hodnik je dug 6 metara i ima samo jednu sijalicu. Želim tri ugradne LED lampe. Budžet dogovor.",
                null, 6 * 24);
        offer(hallway, tarik, 70, "Mogu ugraditi tri LED lampe s prekidačem na oba kraja hodnika.", OfferStatus.PENDING, 5 * 24);

        Task move = published(haris, MOVING, NOVI_GRAD, "Selidba dvosobnog stana na Ilidžu",
                "Selimo se iz Alipašinog Polja na Ilidžu, 4. sprat s liftom. Treba prevesti namještaj, oko 20 kutija, "
                        + "veš mašinu i frižider.", 400, 4 * 24);
        offer(move, adnan, 380, "Kombi i dva radnika, završavamo za jedan dan. Rastavljanje ormara je uključeno.",
                OfferStatus.PENDING, 3 * 24);

        published(amra, MOVING, CENTAR, "Prevoz veš mašine i frižidera",
                "Kupila sam polovne aparate u Novom Sarajevu, treba ih prevesti do Centra i unijeti na 2. sprat bez lifta.",
                90, 8 * 24);

        Task wardrobe = published(emina, FURNITURE, ILIDZA, "Sklapanje IKEA ormara PAX",
                "Ormar PAX 200 x 236 cm s kliznim vratima, sve kutije su u sobi. Treba ga i pričvrstiti za zid.",
                120, 5);
        offer(wardrobe, adnan, 110, "Sklopio sam više PAX ormara, imam alat za pričvršćivanje.", OfferStatus.PENDING, 2);

        published(haris, FURNITURE, NOVO_SARAJEVO, "Montaža kuhinje, osam elemenata",
                "Nova kuhinja iz salona, osam elemenata plus radna ploča. Treba izrezati otvor za sudoper i ploču za kuhanje.",
                350, 10 * 24);

        Task cleaning = published(amra, CLEANING, NOVI_GRAD, "Generalno čišćenje stana od 65 m² nakon renoviranja",
                "Poslije krečenja i postavljanja laminata ostalo je puno prašine. Treba oprati prozore, pod, kupatilo i kuhinju.",
                150, 5 * 24);
        offer(cleaning, selma, 140, "Dolazim s profesionalnim usisivačem i svim sredstvima.", OfferStatus.PENDING, 4 * 24);

        published(emina, CLEANING, STARI_GRAD, "Pranje prozora i roletni",
                "Šest prozora i četiri vanjske roletne u stanu na prvom spratu.", 70, 12 * 24);

        Task painting = published(haris, PAINTING, CENTAR, "Krečenje dvosobnog stana",
                "Stan od 55 m², zidovi su u dobrom stanju. Boju kupujem sam, treba samo rad i pokrivanje namještaja.",
                600, 7 * 24);
        offer(painting, selma, 550, "Krečim u dva sloja, sve pokrijem folijom. Završavam za tri dana.", OfferStatus.PENDING, 6 * 24);
        offer(painting, tarik, 580, "Mogu početi već ovaj vikend.", OfferStatus.PENDING, 5 * 24);

        published(amra, PAINTING, VOGOSCA, "Gletovanje i krečenje jedne sobe",
                "Dječija soba ima pukotine na dva zida. Treba ih zagletovati i okrečiti sobu u svijetlo plavu.",
                null, 15 * 24);

        published(haris, PLUMBING, ILIDZA, "Začepljen odvod u kuhinji",
                "Voda sporo otiče iz sudopera, sredstva iz prodavnice nisu pomogla.", 50, 3);

        published(amra, ELECTRICAL, NOVI_GRAD, "Zamjena osigurača i provjera instalacija",
                "U stanu često izbacuje osigurač kad radi veš mašina. Treba provjeriti instalacije i zamijeniti stare osigurače.",
                100, 20 * 24);

        published(haris, CLEANING, CENTAR, "Čišćenje poslovnog prostora jednom sedmično",
                "Kancelarija od 40 m² u centru grada, čišćenje petkom poslije 17h.", 200, 26 * 24);

        published(emina, FURNITURE, STARI_GRAD, "Montaža televizora na zid",
                "TV od 55 inča, nosač je kupljen. Zid je od betona.", 40, 28 * 24);

        published(emina, PAINTING, ILIDZA, "Farbanje drvene ograde",
                "Ograda oko dvorišta, dužina oko 30 metara. Treba je očistiti i ofarbati lazurom.", 180, 9 * 24);

        published(amra, MOVING, VOGOSCA, "Iznošenje starog namještaja",
                "Stari kauč, dva ormara i krevet treba iznijeti iz kuće i odvesti na deponiju.", null, 11 * 24);

        published(haris, TILING, NOVO_SARAJEVO, "Postavljanje pločica u kupatilu",
                "Kupatilo od 5 m², pločice i ljepilo su kupljeni. Treba skinuti stare pločice sa zida iznad kade.",
                450, 30);

        Task airConditioner = published(emina, AIR_CONDITIONING, CENTAR, "Servis i čišćenje klime prije ljeta",
                "Dvije inverter klime, jedna u dnevnom boravku i jedna u spavaćoj sobi. Nisu servisirane dvije godine.",
                60, 2 * 24);
        offer(airConditioner, tarik, 55, "Čistim unutrašnju i vanjsku jedinicu i provjeravam plin.", OfferStatus.PENDING, 30);

        Task washingMachine = published(haris, APPLIANCES, NOVI_GRAD, "Veš mašina ne izbacuje vodu",
                "Poslije pranja voda ostaje u bubnju, na ekranu piše E21. Mašina je stara pet godina.", null, 26);
        offer(washingMachine, emir, 50, "Najčešće je začepljena pumpa ili filter, popravak je isti dan.", OfferStatus.PENDING, 20);

        published(emina, GARDENING, ILIDZA, "Košenje trave i orezivanje žive ograde",
                "Dvorište od oko 300 m² i živa ograda dužine 20 metara. Otpad treba odvesti.", 80, 4 * 24);

        published(amra, LOCKSMITH, STARI_GRAD, "Zamjena cilindra na ulaznim vratima",
                "Ključ se teško okreće, želim novi sigurnosni cilindar s pet ključeva.", 50, 9);

        published(haris, COMPUTERS, VOGOSCA, "Podešavanje WiFi rutera i printera",
                "Novi ruter od operatera, signal ne dopire do spavaće sobe. Printer treba spojiti na mrežu.", 40, 6 * 24);
    }

    private void seedAmraStory(User amra, User emir, User selma, User adnan) {
        Task siphon = published(amra, PLUMBING, CENTAR, "Zamjena sifona i ventila ispod sudopera",
                "Sifon ispod sudopera je napukao, a ventil za toplu vodu se ne može zatvoriti do kraja.", 90, 6 * 24);
        Offer siphonOffer = offer(siphon, emir, 85, "Imam oba dijela na lageru, mogu doći u četvrtak.",
                OfferStatus.ACCEPTED, 5 * 24);
        offer(siphon, adnan, 95, "Mogu i ja pomoći, dolazim s alatom.", OfferStatus.REJECTED, 5 * 24);
        assign(siphon, siphonOffer, 2 * 24);
        Conversation chat = conversationRepository.findByOffer(siphonOffer).orElseThrow();
        message(chat, amra, "Zdravo Emire, prihvatila sam vašu ponudu. Odgovara li vam četvrtak u 17h?", 47, true);
        message(chat, emir, "Zdravo, odgovara. Možete li mi poslati tačnu adresu i sprat?", 46, true);
        message(chat, amra, "Zmaja od Bosne 12, treći sprat, stan 7. Interfon ne radi pa me nazovite.", 45, true);
        message(chat, emir, "Dogovoreno, vidimo se u četvrtak. Ponijet ću i novi ventil za hladnu vodu za svaki slučaj.", 3, false);

        Task kitchen = published(amra, PAINTING, CENTAR, "Krečenje kuhinje",
                "Kuhinja od 12 m², plafon je požutio iznad šporeta. Treba ga oprati i okrečiti s bojom otpornom na paru.",
                200, 9 * 24);
        Offer kitchenOffer = offer(kitchen, selma, 190, "Koristim periva boja za kuhinje, suši se za dva sata.",
                OfferStatus.ACCEPTED, 8 * 24);
        assign(kitchen, kitchenOffer, 5 * 24);
        kitchen.setStatus(TaskStatus.IN_PROGRESS);
        kitchen.setStartedAt(now.minusHours(20));

        Task bed = published(amra, FURNITURE, CENTAR, "Sklapanje kreveta i komode",
                "Novi bračni krevet i komoda sa šest ladica, sve u kutijama.", 100, 20 * 24);
        Offer bedOffer = offer(bed, adnan, 100, "Sklapam za dva sata, odnosim kartone.", OfferStatus.ACCEPTED, 19 * 24);
        close(bed, bedOffer, 18 * 24, 16 * 24, 15 * 24);
        review(bed, amra, adnan, 5, "Brz, precizan i uredan. Odnio je i sve kartone. Preporuka!", 14 * 24);
        review(bed, adnan, amra, 5, "Sve je bilo spremno kad sam došao, ugodna saradnja.", 14 * 24);

        Task draft = new Task();
        draft.setClient(amra);
        draft.setCategory(category(FURNITURE));
        draft.setMunicipality(municipality(CENTAR));
        draft.setTitle("Postavljanje laminata u spavaćoj sobi");
        draft.setDescription("Soba od 14 m², laminat još nije kupljen.");
        draft.setBudget(new BigDecimal("250"));
        taskRepository.save(draft);
    }

    private void seedFinishedJobs(User emina, User haris, User emir, User selma, User adnan) {
        Task boilerRepair = published(emina, PLUMBING, NOVO_SARAJEVO, "Popravka bojlera koji ne grije",
                "Bojler od 50 litara ne grije vodu, lampica svijetli.", 70, 25 * 24);
        Offer boilerOffer = offer(boilerRepair, emir, 70, "Vjerovatno je grijač, imam ga sa sobom.", OfferStatus.ACCEPTED, 24 * 24);
        close(boilerRepair, boilerOffer, 23 * 24, 22 * 24, 22 * 24);
        review(boilerRepair, emina, emir, 4, "Dobar posao i fer cijena, ali je kasnio pola sata.", 21 * 24);

        Task carpets = published(haris, CLEANING, NOVI_GRAD, "Dubinsko čišćenje tepiha",
                "Tri tepiha u dnevnom boravku i spavaćoj sobi.", 90, 18 * 24);
        Offer carpetOffer = offer(carpets, selma, 85, "Imam mašinu za dubinsko pranje, tepisi su suhi do večeri.",
                OfferStatus.ACCEPTED, 17 * 24);
        close(carpets, carpetOffer, 16 * 24, 15 * 24, 15 * 24);
        review(carpets, haris, selma, 5, "Tepisi izgledaju kao novi. Svaka preporuka!", 14 * 24);

        Task studio = published(emina, MOVING, ILIDZA, "Selidba garsonjere",
                "Garsonjera od 30 m², malo namještaja i petnaestak kutija.", 150, 22 * 24);
        Offer studioOffer = offer(studio, adnan, 150, "Završavamo za pola dana.", OfferStatus.ACCEPTED, 21 * 24);
        close(studio, studioOffer, 20 * 24, 19 * 24, 19 * 24);
        review(studio, emina, adnan, 4, "Sve je stiglo cijelo, samo je jedna kutija bila zgnječena.", 18 * 24);
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
