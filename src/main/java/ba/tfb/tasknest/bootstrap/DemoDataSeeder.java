package ba.tfb.tasknest.bootstrap;

import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.entity.Conversation;
import ba.tfb.tasknest.entity.Message;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.entity.Notification;
import ba.tfb.tasknest.entity.Offer;
import ba.tfb.tasknest.entity.Review;
import ba.tfb.tasknest.entity.Role;
import ba.tfb.tasknest.entity.Task;
import ba.tfb.tasknest.entity.TaskerProfile;
import ba.tfb.tasknest.entity.User;
import ba.tfb.tasknest.entity.enums.AccountStatus;
import ba.tfb.tasknest.entity.enums.ConversationStatus;
import ba.tfb.tasknest.entity.enums.NotificationType;
import ba.tfb.tasknest.entity.enums.OfferStatus;
import ba.tfb.tasknest.entity.enums.RoleName;
import ba.tfb.tasknest.entity.enums.TaskStatus;
import ba.tfb.tasknest.repository.CategoryRepository;
import ba.tfb.tasknest.repository.ConversationRepository;
import ba.tfb.tasknest.repository.MessageRepository;
import ba.tfb.tasknest.repository.MunicipalityRepository;
import ba.tfb.tasknest.repository.NotificationRepository;
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

    private static final Place AMRAS_HOME = new Place("Zmaja od Bosne 12", "43.854947", "18.393707");

    private static final Map<UUID, List<Place>> PLACES = Map.of(
            CENTAR, List.of(
                    new Place("Antuna Hangija 9", "43.860403", "18.403761"),
                    new Place("Kranjčevićeva 17", "43.857957", "18.404534"),
                    new Place("Alipašina 41", "43.861830", "18.410979"),
                    new Place("Koševo 12", "43.860632", "18.414180"),
                    new Place("Patriotske lige 30", "43.867934", "18.412994"),
                    new Place("Čekaluša 41", "43.862770", "18.416351"),
                    new Place("Branilaca Sarajeva 20", "43.857973", "18.419262"),
                    new Place("Valtera Perića 12", "43.856273", "18.410832"),
                    new Place("Skenderija 30", "43.854787", "18.418806"),
                    new Place("Kotromanića 5", "43.855082", "18.409944"),
                    new Place("Reisa Džemaludina Čauševića 4", "43.857000", "18.414107"),
                    new Place("Fra Anđela Zvizdovića 1", "43.857123", "18.405855"),
                    new Place("Mehmeda Spahe 10", "43.859714", "18.419904"),
                    new Place("Džidžikovac 7", "43.860333", "18.415506"),
                    new Place("Višnjik 20", "43.866484", "18.418206"),
                    new Place("Maršala Tita 28", "43.856674", "18.409748")),
            NOVO_SARAJEVO, List.of(
                    new Place("Kolodvorska 12", "43.856426", "18.389188"),
                    new Place("Grbavička 15", "43.852412", "18.400209"),
                    new Place("Splitska 7", "43.850612", "18.403651"),
                    new Place("Zmaja od Bosne 74", "43.852818", "18.384289"),
                    new Place("Vrbanja 1", "43.855382", "18.407331"),
                    new Place("Džemala Bijedića 2", "43.852075", "18.378497"),
                    new Place("Hasana Brkića 2", "43.850671", "18.391308"),
                    new Place("Behdžeta Mutevelića 4", "43.849491", "18.390651"),
                    new Place("Hamdije Čemerlića 2", "43.855531", "18.394100"),
                    new Place("Azize Šaćirbegović 80", "43.849751", "18.379651"),
                    new Place("Trg heroja 10", "43.849648", "18.385751")),
            NOVI_GRAD, List.of(
                    new Place("Bulevar Meše Selimovića 85", "43.846350", "18.360584"),
                    new Place("Džemala Bijedića 160", "43.849723", "18.352354"),
                    new Place("Safeta Zajke 30", "43.852376", "18.355212"),
                    new Place("Adema Buće 303", "43.857673", "18.357433"),
                    new Place("Bosanska 2", "43.842177", "18.349743"),
                    new Place("Nerkeza Smailagića 10", "43.843074", "18.343637"),
                    new Place("Geteova 5", "43.843418", "18.349446"),
                    new Place("Omladinskih radnih brigada 5", "43.829590", "18.347133"),
                    new Place("Rajlovačka cesta 10", "43.880789", "18.311633")),
            STARI_GRAD, List.of(
                    new Place("Ferhadija 15", "43.858920", "18.424659"),
                    new Place("Saraći 70", "43.859266", "18.430841"),
                    new Place("Bistrik 12", "43.856232", "18.429501"),
                    new Place("Kovači 19", "43.860373", "18.432123"),
                    new Place("Logavina 32", "43.861951", "18.428536"),
                    new Place("Mula Mustafe Bašeskije 21", "43.859881", "18.425176")),
            ILIDZA, List.of(
                    new Place("Butmirska cesta 14", "43.829065", "18.311927"),
                    new Place("Dr. Mustafe Pintola 1", "43.830168", "18.310685"),
                    new Place("Mala aleja 13", "43.829792", "18.307103"),
                    new Place("Hrasnička cesta 3", "43.823884", "18.308512"),
                    new Place("Ibrahima Ljubovića 15", "43.831922", "18.303780"),
                    new Place("Velika aleja 2", "43.821616", "18.290438"),
                    new Place("Lužansko polje 5", "43.833986", "18.296522"),
                    new Place("Stupska 2", "43.841557", "18.326944")),
            VOGOSCA, List.of(
                    new Place("Jošanička 10", "43.900346", "18.347226"),
                    new Place("Jošanička 100", "43.901229", "18.339903"),
                    new Place("Igmanska 50", "43.901868", "18.344189"),
                    new Place("Hotonj II 3", "43.900699", "18.370470"),
                    new Place("Hotonj IV 2", "43.896876", "18.374995"),
                    new Place("Ugorsko II 5", "43.892983", "18.353641")));

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
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;
    private final Clock clock;
    private final String password;

    private final Map<User, TaskerProfile> profiles = new HashMap<>();
    private final Map<User, List<Integer>> ratings = new HashMap<>();
    private final List<Runnable> backdating = new ArrayList<>();
    private final Map<UUID, Integer> placesUsed = new HashMap<>();
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
                          NotificationRepository notificationRepository,
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
        this.notificationRepository = notificationRepository;
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

        User amra = user("amra", "Amra", "Hodžić", "+387 61 111 222", 140, RoleName.CLIENT);
        User emina = user("emina", "Emina", "Begić", "+387 62 333 444", 120, RoleName.CLIENT);
        User haris = user("haris", "Haris", "Mehić", null, 95, RoleName.CLIENT);
        user("lejla", "Lejla", "Mujić", null, 200, RoleName.CLIENT, RoleName.ADMIN);
        User nermin = user("nermin", "Nermin", "Hadžić", null, 6, RoleName.CLIENT);
        nermin.setAccountStatus(AccountStatus.SUSPENDED);

        User emir = tasker("emir", "Emir", "Kovačević", 180,
                "Vodoinstalater i električar s 12 godina iskustva",
                "Popravljam kvarove na vodi i struji po cijelom Sarajevu. Dolazim sa svojim alatom i rezervnim dijelovima, "
                        + "a na svaki posao dajem garanciju od šest mjeseci.",
                List.of(PLUMBING, ELECTRICAL, HEATING, APPLIANCES), List.of(CENTAR, STARI_GRAD, NOVO_SARAJEVO), true);
        User selma = tasker("selma", "Selma", "Karić", 160,
                "Čišćenje i krečenje bez stresa",
                "Stan ostavljam čišći nego što sam ga zatekla. Za krečenje donosim folije i sve pokrivam, "
                        + "pa poslije nema mrlja po podu i namještaju.",
                List.of(CLEANING, PAINTING, GARDENING), List.of(NOVI_GRAD, ILIDZA, NOVO_SARAJEVO, CENTAR), true);
        User adnan = tasker("adnan", "Adnan", "Delić", 110,
                "Selidbe i montaža namještaja, imam kombi",
                "Selim stanove i kancelarije, sklapam IKEA i drugi namještaj. Kombi od 12 m³ i pomoćnik za teže stvari "
                        + "su uključeni u cijenu.",
                List.of(MOVING, FURNITURE, CARPENTRY, LOCKSMITH), List.of(CENTAR, NOVO_SARAJEVO, NOVI_GRAD, STARI_GRAD, ILIDZA, VOGOSCA), false);
        User tarik = tasker("tarik", "Tarik", "Hasanović", 21,
                "Mladi električar i moler",
                "Završio sam elektrotehničku školu i radim manje električarske i molerske poslove. Brzo i povoljno.",
                List.of(ELECTRICAL, PAINTING, AIR_CONDITIONING, COMPUTERS), List.of(VOGOSCA, NOVI_GRAD, CENTAR), false);

        User dzenana = user("dzenana", "Dženana", "Alić", "+387 61 555 666", 70, RoleName.CLIENT);
        User kenan = user("kenan", "Kenan", "Imamović", null, 45, RoleName.CLIENT);
        User sanela = user("sanela", "Sanela", "Pašić", "+387 62 777 888", 30, RoleName.CLIENT);

        User mirza = tasker("mirza", "Mirza", "Softić", 150,
                "Stolar i keramičar, radim i brave",
                "Popravljam i pravim namještaj po mjeri, postavljam pločice i mijenjam brave. "
                        + "Prije početka uvijek dođem izmjeriti i dam tačnu cijenu.",
                List.of(CARPENTRY, TILING, LOCKSMITH), List.of(CENTAR, STARI_GRAD, NOVI_GRAD, ILIDZA), true);
        User alen = tasker("alen", "Alen", "Husić", 40,
                "Serviser grijanja, klima i kućanskih aparata",
                "Servisiram plinske kotlove, klime, veš mašine i frižidere. Rezervne dijelove nabavljam isti dan.",
                List.of(HEATING, AIR_CONDITIONING, APPLIANCES), List.of(NOVO_SARAJEVO, NOVI_GRAD, ILIDZA, VOGOSCA), false);
        User lamija = tasker("lamija", "Lamija", "Zukić", 85,
                "Bašta, čišćenje i pomoć s računarom",
                "Uređujem bašte i dvorišta, čistim stanove, a strpljivo pomažem i starijima oko računara i telefona.",
                List.of(GARDENING, CLEANING, COMPUTERS), List.of(CENTAR, NOVO_SARAJEVO, ILIDZA, VOGOSCA), true);

        seedOpenTasks(amra, emina, haris, emir, selma, adnan, tarik);
        seedEveryCategory(dzenana, kenan, sanela, mirza, alen, lamija, tarik);
        seedAmraStory(amra, emir, selma, adnan);
        seedFinishedJobs(emina, haris, emir, selma, adnan);
        seedNewTaskerJobs(dzenana, kenan, mirza, lamija);
        seedOtherOutcomes(emina, haris, nermin, tarik);

        finishProfiles();
        entityManager.flush();
        backdating.forEach(Runnable::run);

        log.info("Demo data created: 15 users with the password from app.demo-data.password, "
                + "log in as amra{} (client), emir{} (tasker) or lejla{} (admin)", EMAIL_DOMAIN, EMAIL_DOMAIN, EMAIL_DOMAIN);
    }

    private void seedOpenTasks(User amra, User emina, User haris,
                               User emir, User selma, User adnan, User tarik) {
        Task sink = published(amra, PLUMBING, CENTAR, "Curi slavina u kupatilu",
                "Slavina na lavabou kapa i kad je potpuno zatvorena. Vjerovatno treba zamijeniti dihtung ili cijelu "
                        + "glavu slavine. Novu slavinu imam ako zatreba.", 60, 20);
        Offer sinkOffer = offer(sink, emir, 55, "Mogu doći sutra poslije 16h, zamjena dihtunga traje pola sata.", OfferStatus.PENDING, 14);
        Conversation sinkChat = conversationOf(sinkOffer);
        message(sinkChat, amra, "Dobar dan, da li biste mogli doći već danas navečer?", 10, true);
        message(sinkChat, emir, "Danas sam do 19h na drugom poslu, ali sutra u 16h sigurno. Ponijeću i rezervni dihtung.", 9, false);
        notify(amra, NotificationType.NEW_OFFER, sink.getId(), "Emir Kovačević offered 55 KM for: " + sink.getTitle(), 14, false);
        notify(amra, NotificationType.NEW_MESSAGE, sinkChat.getId(), "New message about: " + sink.getTitle(), 9, false);

        Task boiler = published(emina, PLUMBING, NOVO_SARAJEVO, "Zamjena bojlera od 80 litara",
                "Stari bojler je počeo curiti. Novi je već u stanu. Treba skinuti stari, montirati novi i odvesti stari na otpad.",
                250, 3 * 24);
        offer(boiler, emir, 230, "Montirao sam desetine ovakvih. Odvoz starog bojlera je uključen.", OfferStatus.PENDING, 60);
        notify(emina, NotificationType.NEW_OFFER, boiler.getId(), "Emir Kovačević offered 230 KM for: " + boiler.getTitle(), 60, true);
        notify(emir, NotificationType.NEW_TASK_IN_AREA, boiler.getId(), "New task in your area: " + boiler.getTitle(), 3 * 24, true);

        Task socket = published(haris, ELECTRICAL, STARI_GRAD, "Nova utičnica i zamjena prekidača u dnevnom boravku",
                "Treba mi jedna dupla utičnica pored televizora i zamjena dva stara prekidača. Zidovi su od cigle.",
                80, 2 * 24);
        notify(emir, NotificationType.NEW_TASK_IN_AREA, socket.getId(), "New task in your area: " + socket.getTitle(), 2 * 24, false);

        Task hallway = published(emina, ELECTRICAL, VOGOSCA, "Rasvjeta u hodniku",
                "Hodnik je dug šest metara i ima samo jednu sijalicu. Želim tri ugradne LED lampe. Otvorena sam za prijedloge.",
                null, 6 * 24);
        offer(hallway, tarik, 70, "Mogu ugraditi tri LED lampe s prekidačem na oba kraja hodnika.", OfferStatus.PENDING, 5 * 24);
        notify(tarik, NotificationType.NEW_TASK_IN_AREA, hallway.getId(), "New task in your area: " + hallway.getTitle(), 6 * 24, true);

        Task move = published(haris, MOVING, NOVI_GRAD, "Selidba dvosobnog stana na Ilidžu",
                "Selimo se s Alipašinog Polja na Ilidžu, četvrti sprat s liftom. Treba prenijeti namještaj, dvadesetak kutija, "
                        + "veš mašinu i frižider.", 400, 4 * 24);
        Offer moveOffer = offer(move, adnan, 380, "Kombi i dva radnika, sve završeno za jedan dan. Rastavljanje ormara je uključeno.",
                OfferStatus.PENDING, 3 * 24);
        Conversation moveChat = conversationOf(moveOffer);
        message(moveChat, haris, "Da li je u cijenu uključeno i nošenje kutija do četvrtog sprata?", 60, true);
        message(moveChat, adnan, "Jeste, nosimo sve do stana. Lift koristimo za kutije, a namještaj ide stepenicama ako ne stane.", 58, true);
        message(moveChat, haris, "Odlično. Može li u subotu ujutro?", 30, true);
        message(moveChat, adnan, "Subota u 8h odgovara. Prihvatite ponudu kad budete spremni, pa ću rezervisati kombi.", 5, false);
        notify(haris, NotificationType.NEW_OFFER, move.getId(), "Adnan Delić offered 380 KM for: " + move.getTitle(), 3 * 24, true);
        notify(haris, NotificationType.NEW_MESSAGE, moveChat.getId(), "New message about: " + move.getTitle(), 5, false);

        published(amra, MOVING, CENTAR, "Prevoz veš mašine i frižidera",
                "Kupila sam polovne aparate na Novom Sarajevu. Treba ih prevesti do Centra i unijeti na drugi sprat bez lifta.",
                90, 8 * 24);

        Task wardrobe = published(emina, FURNITURE, ILIDZA, "Montaža IKEA PAX ormara",
                "PAX ormar 200 x 236 cm s kliznim vratima, sve kutije su u sobi. Ormar treba i pričvrstiti za zid.",
                120, 5);
        offer(wardrobe, adnan, 110, "Sklopio sam mnogo PAX ormara i imam alat za pričvršćivanje za zid.", OfferStatus.PENDING, 2);
        notify(emina, NotificationType.NEW_OFFER, wardrobe.getId(), "Adnan Delić offered 110 KM for: " + wardrobe.getTitle(), 2, false);
        notify(adnan, NotificationType.NEW_TASK_IN_AREA, wardrobe.getId(), "New task in your area: " + wardrobe.getTitle(), 5, true);

        Task kitchenUnits = published(haris, FURNITURE, NOVO_SARAJEVO, "Montaža kuhinje s osam elemenata",
                "Nova kuhinja iz salona, osam elemenata i radna ploča. Treba izrezati otvore za sudoperu i ploču za kuhanje.",
                350, 10 * 24);
        notify(adnan, NotificationType.NEW_TASK_IN_AREA, kitchenUnits.getId(), "New task in your area: " + kitchenUnits.getTitle(), 10 * 24, false);

        Task cleaning = published(amra, CLEANING, NOVI_GRAD, "Generalno čišćenje stana od 65 m² nakon renoviranja",
                "Krečenje i novi laminat ostavili su mnogo prašine. Treba očistiti prozore, podove, kupatilo i kuhinju.",
                150, 5 * 24);
        offer(cleaning, selma, 140, "Dolazim s profesionalnim usisivačem i svim sredstvima.", OfferStatus.PENDING, 4 * 24);
        notify(amra, NotificationType.NEW_OFFER, cleaning.getId(), "Selma Karić offered 140 KM for: " + cleaning.getTitle(), 4 * 24, true);

        published(emina, CLEANING, STARI_GRAD, "Pranje prozora i roletni",
                "Šest prozora i četiri vanjske roletne u stanu na prvom spratu.", 70, 12 * 24);

        Task painting = published(haris, PAINTING, CENTAR, "Krečenje dvosobnog stana",
                "Stan od 55 m², zidovi su u dobrom stanju. Boju kupujem ja, treba mi samo rad i zaštita namještaja.",
                600, 7 * 24);
        offer(painting, selma, 550, "Dva sloja, sve pokrivam folijom. Gotovo za tri dana.", OfferStatus.PENDING, 6 * 24);
        offer(painting, tarik, 580, "Mogu početi ovog vikenda.", OfferStatus.PENDING, 5 * 24);
        notify(haris, NotificationType.NEW_OFFER, painting.getId(), "Selma Karić offered 550 KM for: " + painting.getTitle(), 6 * 24, true);
        notify(haris, NotificationType.NEW_OFFER, painting.getId(), "Tarik Hasanović offered 580 KM for: " + painting.getTitle(), 5 * 24, true);

        published(amra, PAINTING, VOGOSCA, "Gletovanje i krečenje jedne sobe",
                "Dječija soba ima pukotine na dva zida. Treba ih zagletovati i sobu okrečiti u svijetloplavo.",
                null, 13 * 24);

        published(haris, PLUMBING, ILIDZA, "Začepljen odvod u kuhinji",
                "Voda sporo otiče iz sudopera, a sredstva iz prodavnice nisu pomogla.", 50, 3);

        published(amra, ELECTRICAL, NOVI_GRAD, "Zamjena osigurača i provjera instalacije",
                "Osigurač često iskače kad radi veš mašina. Treba provjeriti instalaciju i zamijeniti stare osigurače.",
                100, 11 * 24);

        published(haris, CLEANING, CENTAR, "Sedmično čišćenje kancelarije",
                "Kancelarija od 40 m² u centru grada, čišćenje petkom poslije 17h.", 200, 9 * 24);

        published(emina, FURNITURE, STARI_GRAD, "Montaža televizora na zid",
                "Televizor od 55 inča, nosač je već kupljen. Zid je betonski.", 40, 14 * 24);

        Task fence = published(emina, PAINTING, ILIDZA, "Farbanje drvene ograde",
                "Ograda oko bašte duga je tridesetak metara. Treba je očistiti i premazati lazurom.", 180, 9 * 24);
        notify(selma, NotificationType.NEW_TASK_IN_AREA, fence.getId(), "New task in your area: " + fence.getTitle(), 9 * 24, true);

        published(amra, MOVING, VOGOSCA, "Odvoz starog namještaja",
                "Stari trosjed, dva ormara i krevet treba iznijeti iz kuće i odvesti na deponiju.", null, 11 * 24);

        published(haris, TILING, NOVO_SARAJEVO, "Postavljanje pločica na zid kupatila",
                "Kupatilo od 5 m², pločice i ljepilo su kupljeni. Prvo treba skinuti stare pločice iznad kade.",
                450, 30);

        Task airConditioner = published(emina, AIR_CONDITIONING, CENTAR, "Servis i čišćenje klima uređaja pred ljeto",
                "Dva inverter uređaja, jedan u dnevnom boravku i jedan u spavaćoj sobi. Nisu servisirani dvije godine.",
                60, 2 * 24);
        offer(airConditioner, tarik, 55, "Čistim unutrašnju i vanjsku jedinicu i provjeravam plin.", OfferStatus.PENDING, 30);

        Task washingMachine = published(haris, APPLIANCES, NOVI_GRAD, "Veš mašina ne izbacuje vodu",
                "Nakon pranja voda ostaje u bubnju, a na ekranu piše E21. Mašina je stara pet godina.", null, 26);
        offer(washingMachine, emir, 50, "Najčešće je začepljena pumpa ili filter, popravka isti dan.", OfferStatus.PENDING, 20);
        notify(haris, NotificationType.NEW_OFFER, washingMachine.getId(), "Emir Kovačević offered 50 KM for: " + washingMachine.getTitle(), 20, false);

        Task lawn = published(emina, GARDENING, ILIDZA, "Košenje trave i orezivanje živice",
                "Bašta od oko 300 m² i živica duga 20 metara. Treba odvesti i otpad.", 80, 4 * 24);
        notify(selma, NotificationType.NEW_TASK_IN_AREA, lawn.getId(), "New task in your area: " + lawn.getTitle(), 4 * 24, false);

        published(amra, LOCKSMITH, STARI_GRAD, "Zamjena cilindra na ulaznim vratima",
                "Ključ se teško okreće. Želim novi sigurnosni cilindar s pet ključeva.", 50, 9);

        published(haris, COMPUTERS, VOGOSCA, "Podešavanje Wi-Fi rutera i printera",
                "Novi ruter od provajdera, ali signal ne dopire do spavaće sobe. Treba povezati i printer.", 40, 6 * 24);

        Task emirsAirConditioner = published(emir, AIR_CONDITIONING, NOVO_SARAJEVO, "Ugradnja klime u spavaću sobu",
                "Klima je kupljena i treba je ugraditi na trećem spratu. Vanjska jedinica ide na balkon.", 150, 2 * 24);
        offer(emirsAirConditioner, tarik, 140, "Imam iskustva s ugradnjom, bušenje zida i vakumiranje su uključeni.", OfferStatus.PENDING, 40);
        notify(emir, NotificationType.NEW_OFFER, emirsAirConditioner.getId(),
                "Tarik Hasanović offered 140 KM for: " + emirsAirConditioner.getTitle(), 40, false);
    }

    private void seedEveryCategory(User dzenana, User kenan, User sanela,
                                   User mirza, User alen, User lamija, User tarik) {
        Task backsplash = published(kenan, TILING, NOVI_GRAD, "Pločice u kuhinji iznad radne ploče",
                "Zid od 3 m² između gornjih i donjih elemenata. Pločice i ljepilo su kupljeni.", 120, 2 * 24);
        offer(backsplash, mirza, 110, "Mogu doći u srijedu, fugovanje je uključeno u cijenu.", OfferStatus.PENDING, 30);
        notify(kenan, NotificationType.NEW_OFFER, backsplash.getId(), "Mirza Softić offered 110 KM for: " + backsplash.getTitle(), 30, false);
        notify(mirza, NotificationType.NEW_TASK_IN_AREA, backsplash.getId(), "New task in your area: " + backsplash.getTitle(), 2 * 24, true);

        published(sanela, TILING, CENTAR, "Zamjena napuklih pločica na terasi",
                "Desetak pločica na terasi je popucalo od mraza. Imam rezervne iz iste serije.", 90, 5 * 24);

        Task wardrobeDoors = published(dzenana, CARPENTRY, STARI_GRAD, "Popravka kliznih vrata na ormaru",
                "Vrata ormara ispadaju iz šine i zapinju pri otvaranju. Ormar je star osam godina.", 50, 20);
        offer(wardrobeDoors, mirza, 45, "Najčešće treba zamijeniti točkiće, a njih imam sa sobom.", OfferStatus.PENDING, 12);
        notify(mirza, NotificationType.NEW_TASK_IN_AREA, wardrobeDoors.getId(), "New task in your area: " + wardrobeDoors.getTitle(), 20, false);

        published(kenan, CARPENTRY, CENTAR, "Izrada polica po mjeri za niše",
                "Dvije niše u dnevnom boravku, svaka 90 x 40 cm. Želim police od hrastovog furnira.", 220, 3 * 24);

        Task stairs = published(sanela, CARPENTRY, NOVI_GRAD, "Brušenje i lakiranje drvenih stepenica",
                "Unutrašnje stepenice imaju 14 gazišta, a lak je izlizan na sredini.", 400, 6 * 24);
        offer(stairs, mirza, 380, "Brusim bez prašine po kući, dva sloja laka, gotovo za tri dana.", OfferStatus.PENDING, 5 * 24);
        notify(sanela, NotificationType.NEW_OFFER, stairs.getId(), "Mirza Softić offered 380 KM for: " + stairs.getTitle(), 5 * 24, true);

        Task leakingAirConditioner = published(dzenana, AIR_CONDITIONING, ILIDZA, "Klima curi vodu u sobi",
                "Iz unutrašnje jedinice kaplje voda niz zid. Vjerovatno je začepljen odvod kondenzata.", 40, 10);
        offer(leakingAirConditioner, alen, 35, "Očistim odvod i filtere, pola sata posla.", OfferStatus.PENDING, 6);
        notify(dzenana, NotificationType.NEW_OFFER, leakingAirConditioner.getId(),
                "Alen Husić offered 35 KM for: " + leakingAirConditioner.getTitle(), 6, false);
        notify(alen, NotificationType.NEW_TASK_IN_AREA, leakingAirConditioner.getId(),
                "New task in your area: " + leakingAirConditioner.getTitle(), 10, true);

        Task boilerService = published(kenan, HEATING, NOVO_SARAJEVO, "Servis plinskog kotla prije sezone",
                "Kotao Vaillant, zadnji servis bio je prije dvije godine. Treba provjeriti i ekspanzionu posudu.", 80, 24);
        Offer boilerServiceOffer = offer(boilerService, alen, 75, "Ovlašteni sam serviser za Vaillant, mogu doći sutra ujutro.",
                OfferStatus.PENDING, 18);
        Conversation boilerChat = conversationOf(boilerServiceOffer);
        message(boilerChat, kenan, "Da li je u cijenu uključena i analiza dimnih plinova?", 16, true);
        message(boilerChat, alen, "Jeste, dobijate i zapisnik o servisu za upravitelja zgrade.", 15, false);
        notify(kenan, NotificationType.NEW_OFFER, boilerService.getId(), "Alen Husić offered 75 KM for: " + boilerService.getTitle(), 18, true);
        notify(kenan, NotificationType.NEW_MESSAGE, boilerChat.getId(), "New message about: " + boilerService.getTitle(), 15, false);
        notify(alen, NotificationType.NEW_TASK_IN_AREA, boilerService.getId(), "New task in your area: " + boilerService.getTitle(), 24, true);

        published(sanela, HEATING, VOGOSCA, "Radijatori se ne zagrijavaju do kraja",
                "Gornji dio radijatora ostaje hladan u tri sobe. Možda treba ozračiti sistem.", 60, 3 * 24);

        published(dzenana, HEATING, ILIDZA, "Ugradnja termostatskih ventila",
                "Pet radijatora, termostatski ventili su već kupljeni.", 120, 7 * 24);

        published(sanela, APPLIANCES, NOVO_SARAJEVO, "Frižider ne hladi",
                "Svjetlo u frižideru radi, ali ne hladi. Zamrzivač radi normalno.", null, 6);

        Task dishwasher = published(kenan, APPLIANCES, NOVI_GRAD, "Ugradnja mašine za suđe",
                "Ugradna mašina od 60 cm, priključci za vodu i odvod već postoje.", 60, 4 * 24);
        offer(dishwasher, alen, 55, "Ugradnja i probno pranje, sat vremena.", OfferStatus.PENDING, 3 * 24);
        notify(kenan, NotificationType.NEW_OFFER, dishwasher.getId(), "Alen Husić offered 55 KM for: " + dishwasher.getTitle(), 3 * 24, true);

        Task basementLock = published(dzenana, LOCKSMITH, CENTAR, "Otvaranje zaključanih vrata podruma",
                "Ključ od podruma je izgubljen. Treba otvoriti vrata i ugraditi novu bravu.", 70, 2 * 24);
        Offer basementLockOffer = offer(basementLock, mirza, 60, "Otvaram bez oštećenja vrata, a novu bravu donosim.",
                OfferStatus.PENDING, 40);
        Conversation lockChat = conversationOf(basementLockOffer);
        message(lockChat, dzenana, "Možete li doći i u subotu?", 30, true);
        message(lockChat, mirza, "Mogu, u subotu radim do 14h.", 29, true);
        notify(dzenana, NotificationType.NEW_OFFER, basementLock.getId(), "Mirza Softić offered 60 KM for: " + basementLock.getTitle(), 40, true);

        published(kenan, LOCKSMITH, STARI_GRAD, "Ugradnja dodatne brave na ulazna vrata",
                "Želim dodatnu sigurnosnu bravu i sigurnosni lanac.", 80, 8 * 24);

        Task orchard = published(sanela, GARDENING, VOGOSCA, "Orezivanje voćki u dvorištu",
                "Šest stabala jabuke i šljive treba orezati, a granje odvesti.", 100, 2 * 24);
        offer(orchard, lamija, 90, "Orezujem i odvozim granje isti dan.", OfferStatus.PENDING, 36);
        notify(sanela, NotificationType.NEW_OFFER, orchard.getId(), "Lamija Zukić offered 90 KM for: " + orchard.getTitle(), 36, false);
        notify(lamija, NotificationType.NEW_TASK_IN_AREA, orchard.getId(), "New task in your area: " + orchard.getTitle(), 2 * 24, true);

        published(kenan, GARDENING, NOVO_SARAJEVO, "Sadnja živice uz ogradu",
                "Dvadesetak sadnica tuje treba posaditi uz ogradu od 15 metara. Sadnice su kupljene.", 120, 9 * 24);

        Task laptop = published(dzenana, COMPUTERS, CENTAR, "Instalacija Windowsa i prebacivanje podataka",
                "Novi laptop, treba instalirati programe i prebaciti slike i dokumente sa starog.", 50, 24);
        offer(laptop, lamija, 45, "Prebacim sve podatke i podesim štampač ako ga imate.", OfferStatus.PENDING, 20);
        offer(laptop, tarik, 40, "Mogu i danas navečer.", OfferStatus.PENDING, 18);
        notify(lamija, NotificationType.NEW_TASK_IN_AREA, laptop.getId(), "New task in your area: " + laptop.getTitle(), 24, false);

        published(sanela, COMPUTERS, NOVO_SARAJEVO, "Pomoć roditeljima oko pametnog telefona",
                "Novi telefon za roditelje, treba podesiti Viber i mail i povećati slova.", 30, 5 * 24);
    }

    private void seedNewTaskerJobs(User dzenana, User kenan, User mirza, User lamija) {
        Task kitchenDoors = published(dzenana, CARPENTRY, CENTAR, "Popravka vrata na kuhinjskim elementima",
                "Četiri vrata visila su ukoso, a jedna šarka je pukla.", 40, 16 * 24);
        Offer kitchenDoorsOffer = offer(kitchenDoors, mirza, 40, "Mijenjam šarke i podešavam sva vrata.", OfferStatus.ACCEPTED, 15 * 24);
        close(kitchenDoors, kitchenDoorsOffer, 15 * 24, 14 * 24, 14 * 24);
        review(kitchenDoors, dzenana, mirza, 5, "Došao na vrijeme i sve popravio za sat. Vrata sad zatvaraju kao nova.", 13 * 24);
        review(kitchenDoors, mirza, dzenana, 5, "Jasan dogovor i ljubazna domaćica.", 13 * 24);
        notify(mirza, NotificationType.REVIEW_RECEIVED, kitchenDoors.getId(), "You received a review for: " + kitchenDoors.getTitle(), 13 * 24, true);

        Task balcony = published(kenan, GARDENING, NOVO_SARAJEVO, "Sadnja cvijeća na balkonu",
                "Šest žardinjera, treba nabaviti zemlju i posaditi sezonsko cvijeće.", 60, 11 * 24);
        Offer balconyOffer = offer(balcony, lamija, 60, "Donosim zemlju i sadnice, a vi birate boje.", OfferStatus.ACCEPTED, 10 * 24);
        close(balcony, balconyOffer, 10 * 24, 9 * 24, 9 * 24);
        review(balcony, kenan, lamija, 4, "Lijepo urađeno, samo je jedna žardinjera ostala prazna dan duže.", 8 * 24);
        notify(lamija, NotificationType.REVIEW_RECEIVED, balcony.getId(), "You received a review for: " + balcony.getTitle(), 8 * 24, false);
    }

    private void seedAmraStory(User amra, User emir, User selma, User adnan) {
        Task siphon = published(amra, PLUMBING, CENTAR, "Zamjena sifona i ventila ispod sudopera",
                "Sifon ispod sudopera je napukao, a ventil za toplu vodu ne zatvara do kraja.", 90, 6 * 24);
        locate(siphon, AMRAS_HOME);
        Offer siphonOffer = offer(siphon, emir, 85, "Imam oba dijela na lageru i mogu doći u četvrtak.",
                OfferStatus.ACCEPTED, 5 * 24);
        offer(siphon, adnan, 95, "Mogu i ja pomoći, ponijeću alat.", OfferStatus.REJECTED, 5 * 24);
        assign(siphon, siphonOffer, 2 * 24);
        Conversation chat = conversationOf(siphonOffer);
        message(chat, amra, "Zdravo Emire, prihvatila sam vašu ponudu. Odgovara li vam četvrtak u 17h?", 47, true);
        message(chat, emir, "Zdravo, odgovara. Možete li mi poslati tačnu adresu i sprat?", 46, true);
        message(chat, amra, "Zmaja od Bosne 12, treći sprat, stan 7. Interfon ne radi, pa me nazovite.", 45, true);
        message(chat, emir, "Dogovoreno, vidimo se u četvrtak. Ponijeću i novi ventil za hladnu vodu, za svaki slučaj.", 3, false);
        notify(emir, NotificationType.OFFER_ACCEPTED, siphon.getId(), "You were hired for: " + siphon.getTitle(), 2 * 24, true);
        notify(amra, NotificationType.NEW_MESSAGE, chat.getId(), "New message about: " + siphon.getTitle(), 3, false);

        Task kitchen = published(amra, PAINTING, CENTAR, "Krečenje kuhinje",
                "Kuhinja od 12 m², plafon iznad šporeta je požutio. Treba ga oprati i okrečiti bojom otpornom na paru.",
                200, 9 * 24);
        Offer kitchenOffer = offer(kitchen, selma, 190, "Koristim perivu boju za kuhinje koja se suši za dva sata.",
                OfferStatus.ACCEPTED, 8 * 24);
        assign(kitchen, kitchenOffer, 5 * 24);
        kitchen.setStatus(TaskStatus.IN_PROGRESS);
        kitchen.setStartedAt(now.minusHours(20));
        notify(selma, NotificationType.OFFER_ACCEPTED, kitchen.getId(), "You were hired for: " + kitchen.getTitle(), 5 * 24, true);
        notify(amra, NotificationType.TASK_STARTED, kitchen.getId(), "Work has started on your task: " + kitchen.getTitle(), 20, true);

        Task bed = published(amra, FURNITURE, CENTAR, "Sklapanje kreveta i komode",
                "Novi bračni krevet i komoda sa šest ladica, sve je još u kutijama.", 100, 20 * 24);
        Offer bedOffer = offer(bed, adnan, 100, "Gotovo za dva sata, a karton odnosim.", OfferStatus.ACCEPTED, 19 * 24);
        close(bed, bedOffer, 18 * 24, 16 * 24, 15 * 24);
        review(bed, amra, adnan, 5, "Brz, precizan i uredan. Odnio je i sve kartone. Preporuka!", 14 * 24);
        review(bed, adnan, amra, 5, "Sve je bilo spremno kad sam došao. Ugodna saradnja.", 14 * 24);

        Task draft = new Task();
        draft.setClient(amra);
        draft.setCategory(category(FURNITURE));
        draft.setMunicipality(municipality(CENTAR));
        locate(draft, CENTAR);
        draft.setTitle("Postavljanje laminata u spavaćoj sobi");
        draft.setDescription("Soba od 14 m², laminat još nije kupljen.");
        draft.setBudget(new BigDecimal("250"));
        taskRepository.save(draft);
    }

    private void seedFinishedJobs(User emina, User haris, User emir, User selma, User adnan) {
        Task boilerRepair = published(emina, PLUMBING, NOVO_SARAJEVO, "Popravka bojlera koji ne grije",
                "Bojler od 50 litara, lampica svijetli, ali voda ostaje hladna.", 70, 25 * 24);
        Offer boilerOffer = offer(boilerRepair, emir, 70, "Vjerovatno je grijač, a jedan imam sa sobom.", OfferStatus.ACCEPTED, 24 * 24);
        close(boilerRepair, boilerOffer, 23 * 24, 22 * 24, 22 * 24);
        review(boilerRepair, emina, emir, 4, "Dobar posao i korektna cijena, ali je kasnio pola sata.", 21 * 24);
        review(boilerRepair, emir, emina, 5, "Ljubazna i tačna, sve je bilo dogovoreno unaprijed.", 21 * 24);
        notify(emir, NotificationType.REVIEW_RECEIVED, boilerRepair.getId(), "You received a review for: " + boilerRepair.getTitle(), 21 * 24, true);

        Task carpets = published(haris, CLEANING, NOVI_GRAD, "Dubinsko čišćenje tepiha",
                "Tri tepiha u dnevnom boravku i spavaćoj sobi.", 90, 18 * 24);
        Offer carpetOffer = offer(carpets, selma, 85, "Imam mašinu za dubinsko pranje, tepisi su suhi do večeri.",
                OfferStatus.ACCEPTED, 17 * 24);
        close(carpets, carpetOffer, 16 * 24, 15 * 24, 15 * 24);
        review(carpets, haris, selma, 5, "Tepisi izgledaju kao novi. Svaka preporuka!", 14 * 24);
        review(carpets, selma, haris, 4, "Sve je bilo u redu, samo je plaćanje kasnilo jedan dan.", 14 * 24);

        Task studio = published(emina, MOVING, ILIDZA, "Selidba garsonjere",
                "Garsonjera od 30 m², malo namještaja i petnaestak kutija.", 150, 22 * 24);
        Offer studioOffer = offer(studio, adnan, 150, "Gotovo za pola dana.", OfferStatus.ACCEPTED, 21 * 24);
        close(studio, studioOffer, 20 * 24, 19 * 24, 19 * 24);
        review(studio, emina, adnan, 4, "Sve je stiglo cijelo, samo je jedna kutija bila zgnječena.", 18 * 24);
        notify(adnan, NotificationType.REVIEW_RECEIVED, studio.getId(), "You received a review for: " + studio.getTitle(), 18 * 24, true);

        Task garden = published(emir, GARDENING, ILIDZA, "Uređenje bašte i sadnja cvijeća",
                "Baštu ispred kuće treba prekopati, posaditi sezonsko cvijeće i pokositi travu.", 120, 13 * 24);
        Offer gardenOffer = offer(garden, selma, 120, "Donosim sadnice i zemlju, sve završavam za jedan dan.", OfferStatus.ACCEPTED, 12 * 24);
        close(garden, gardenOffer, 12 * 24, 11 * 24, 11 * 24);
        review(garden, emir, selma, 5, "Bašta nikad nije bila ljepša. Hvala!", 10 * 24);
        review(garden, selma, emir, 5, "Jasno je rekao šta treba i platio odmah.", 10 * 24);
    }

    private void seedOtherOutcomes(User emina, User haris, User nermin, User tarik) {
        Task bathroomLight = published(emina, ELECTRICAL, CENTAR, "Montaža rasvjete u kupatilu",
                "Stara plafonjerka u kupatilu ne radi. Nova je kupljena i treba je montirati.", 50, 4 * 24);
        Offer lightOffer = offer(bathroomLight, tarik, 45, "Mogu doći sutra, montaža traje sat vremena.", OfferStatus.ACCEPTED, 3 * 24);
        assign(bathroomLight, lightOffer, 3 * 24);
        bathroomLight.setStartedAt(now.minusHours(30));
        bathroomLight.setCompletedAt(now.minusHours(3));
        bathroomLight.setStatus(TaskStatus.COMPLETED);
        notify(tarik, NotificationType.OFFER_ACCEPTED, bathroomLight.getId(), "You were hired for: " + bathroomLight.getTitle(), 3 * 24, true);
        notify(emina, NotificationType.TASK_COMPLETED, bathroomLight.getId(),
                "Work has been completed on your task: " + bathroomLight.getTitle(), 3, false);

        Task door = published(haris, LOCKSMITH, NOVI_GRAD, "Ugradnja sigurnosnih vrata",
                "Sigurnosna vrata su kupljena, treba skinuti stara i ugraditi nova.", 300, 12 * 24);
        door.setStatus(TaskStatus.CANCELLED);

        Task basement = published(emina, CLEANING, NOVO_SARAJEVO, "Čišćenje podruma",
                "Podrum od 15 m² pun starih stvari, treba ga isprazniti i počistiti.", 60, 35 * 24);
        basement.setStatus(TaskStatus.EXPIRED);
        notify(emina, NotificationType.TASK_EXPIRED, basement.getId(), "Your task has expired: " + basement.getTitle(), 5 * 24, true);

        Task spam = published(nermin, COMPUTERS, CENTAR, "Zarada od kuće, 500 KM dnevno",
                "Tražimo saradnike za rad od kuće, bez iskustva. Javite se na WhatsApp za više informacija.", null, 5 * 24);
        spam.setStatus(TaskStatus.REMOVED);
        notify(nermin, NotificationType.TASK_REMOVED, spam.getId(),
                "Your task was removed by a moderator: " + spam.getTitle() + ". Reason: Spam ili reklama", 4 * 24, false);
    }

    private User user(String login, String firstName, String lastName, String phone, int daysAgo, RoleName... roleNames) {
        User user = new User();
        user.setEmail(login + EMAIL_DOMAIN);
        user.setPasswordHash(passwordHash);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setPhone(phone);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setEmailVerified(true);
        Arrays.stream(roleNames).map(this::role).forEach(user.getRoles()::add);
        User saved = userRepository.save(user);
        backdate("users", saved.getId(), now.minusDays(daysAgo));
        return saved;
    }

    private User tasker(String login, String firstName, String lastName, int daysAgo, String headline, String bio,
                        List<UUID> categoryIds, List<UUID> municipalityIds, boolean verified) {
        User tasker = user(login, firstName, lastName, "+387 65 000 " + (100 + profiles.size()), daysAgo,
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
        locate(task, municipalityId);
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

    private void locate(Task task, UUID municipalityId) {
        List<Place> places = PLACES.get(municipalityId);
        int used = placesUsed.merge(municipalityId, 1, Integer::sum) - 1;
        locate(task, places.get(used % places.size()));
    }

    private void locate(Task task, Place place) {
        task.setAddressLine(place.address());
        task.setLatitude(new BigDecimal(place.latitude()));
        task.setLongitude(new BigDecimal(place.longitude()));
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
        if (!reviewee.getId().equals(task.getClient().getId())) {
            ratings.computeIfAbsent(reviewee, key -> new ArrayList<>()).add(rating);
        }
    }

    private Conversation conversationOf(Offer offer) {
        return conversationRepository.findByOffer(offer).orElseThrow();
    }

    private void notify(User recipient, NotificationType type, UUID relatedEntityId, String content, int hoursAgo, boolean read) {
        Notification notification = new Notification();
        notification.setRecipient(recipient);
        notification.setType(type);
        notification.setRelatedEntityId(relatedEntityId);
        notification.setContent(content);
        notification.setRead(read);
        Notification saved = notificationRepository.save(notification);
        backdate("notifications", saved.getId(), now.minusHours(hoursAgo));
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

    private record Place(String address, String latitude, String longitude) {
    }
}
