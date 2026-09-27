package ba.tfb.tasknest.realtime;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.auth.AuthResponse;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.dto.conversation.SendMessageRequest;
import ba.tfb.tasknest.dto.offer.CreateOfferRequest;
import ba.tfb.tasknest.dto.task.CreateTaskRequest;
import ba.tfb.tasknest.entity.Conversation;
import ba.tfb.tasknest.entity.Offer;
import ba.tfb.tasknest.repository.CategoryRepository;
import ba.tfb.tasknest.repository.ConversationRepository;
import ba.tfb.tasknest.repository.MessageRepository;
import ba.tfb.tasknest.repository.MunicipalityRepository;
import ba.tfb.tasknest.repository.NotificationRepository;
import ba.tfb.tasknest.repository.OfferRepository;
import ba.tfb.tasknest.repository.RefreshTokenRepository;
import ba.tfb.tasknest.repository.TaskRepository;
import ba.tfb.tasknest.repository.TaskerProfileRepository;
import ba.tfb.tasknest.repository.UserRepository;
import ba.tfb.tasknest.service.AuthService;
import ba.tfb.tasknest.service.ConversationService;
import ba.tfb.tasknest.service.OfferService;
import ba.tfb.tasknest.service.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RealtimeIntegrationTest extends AbstractIntegrationTest {

    @Value("${local.server.port}")
    private int port;

    @Autowired private AuthService authService;
    @Autowired private TaskService taskService;
    @Autowired private OfferService offerService;
    @Autowired private ConversationService conversationService;
    @Autowired private SimpUserRegistry userRegistry;
    @Autowired private TransactionTemplate transactionTemplate;

    @Autowired private UserRepository userRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private OfferRepository offerRepository;
    @Autowired private MessageRepository messageRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MunicipalityRepository municipalityRepository;
    @Autowired private ConversationRepository conversationRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private TaskerProfileRepository taskerProfileRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    private final List<StompSession> sessions = new ArrayList<>();
    private WebSocketStompClient stompClient;

    private AuthResponse client;
    private AuthResponse tasker;
    private AuthResponse stranger;

    @BeforeEach
    void setUp() {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());

        client = register("live.client@test.ba");
        tasker = register("live.tasker@test.ba");
        stranger = register("live.stranger@test.ba");
        authService.activateTaskerRole(tasker.userId());
    }

    @AfterEach
    void tearDown() {
        sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
        stompClient.stop();

        messageRepository.deleteAll();
        notificationRepository.deleteAll();
        conversationRepository.deleteAll();
        transactionTemplate.executeWithoutResult(status ->
                taskRepository.findAll().forEach(task -> task.setAcceptedOffer(null)));
        offerRepository.deleteAll();
        taskRepository.deleteAll();
        taskerProfileRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("A connection without a valid access token is refused")
    void connect_isRefused_withoutAValidToken() {
        assertThatThrownBy(() -> connect("Bearer not-a-token"))
                .isInstanceOf(ExecutionException.class);
    }

    @Test
    @DisplayName("A new message reaches both participants and nobody else")
    void sendMessage_reachesBothParticipants() throws Exception {
        // Arrange
        UUID conversationId = openConversation();
        BlockingQueue<Map<String, Object>> toTasker = listen(tasker, "/user/queue/messages");
        BlockingQueue<Map<String, Object>> toClient = listen(client, "/user/queue/messages");
        BlockingQueue<Map<String, Object>> toStranger = listen(stranger, "/user/queue/messages");

        // Act
        conversationService.sendMessage(conversationId, client.userId(), new SendMessageRequest("Can you come at 10?"));

        // Assert
        Map<String, Object> received = toTasker.poll(5, TimeUnit.SECONDS);
        assertThat(received).containsEntry("conversationId", conversationId.toString());
        assertThat(message(received)).containsEntry("content", "Can you come at 10?")
                .containsEntry("senderId", client.userId().toString());
        assertThat(toClient.poll(5, TimeUnit.SECONDS)).isNotNull();
        assertThat(toStranger.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    @DisplayName("Reading a conversation tells the sender that their messages were read")
    void markAsRead_tellsTheSender() throws Exception {
        // Arrange
        UUID conversationId = openConversation();
        conversationService.sendMessage(conversationId, client.userId(), new SendMessageRequest("Hello"));
        BlockingQueue<Map<String, Object>> reads = listen(client, "/user/queue/reads");

        // Act
        conversationService.markAsRead(conversationId, tasker.userId());

        // Assert
        Map<String, Object> received = reads.poll(5, TimeUnit.SECONDS);
        assertThat(received).containsEntry("conversationId", conversationId.toString())
                .containsEntry("readerId", tasker.userId().toString())
                .containsKey("readAt");
    }

    @Test
    @DisplayName("A new notification is pushed to its recipient right after it is stored")
    void notification_isPushedToItsRecipient() throws Exception {
        // Arrange
        UUID taskId = publishedTask();
        BlockingQueue<Map<String, Object>> toClient = listen(client, "/user/queue/notifications");
        BlockingQueue<Map<String, Object>> toStranger = listen(stranger, "/user/queue/notifications");

        // Act
        offerService.submitOffer(taskId, tasker.userId(), new CreateOfferRequest(new BigDecimal("40"), null));

        // Assert
        Map<String, Object> received = toClient.poll(5, TimeUnit.SECONDS);
        assertThat(received).containsEntry("type", "NEW_OFFER")
                .containsEntry("relatedEntityId", taskId.toString())
                .containsEntry("read", false);
        assertThat(toStranger.poll(500, TimeUnit.MILLISECONDS)).isNull();
    }

    @Test
    @DisplayName("Nothing is pushed when the change is rolled back")
    void rolledBackMessage_pushesNothing() throws Exception {
        // Arrange
        UUID conversationId = openConversation();
        BlockingQueue<Map<String, Object>> toTasker = listen(tasker, "/user/queue/messages");

        // Act
        transactionTemplate.executeWithoutResult(status -> {
            conversationService.sendMessage(conversationId, client.userId(), new SendMessageRequest("Never mind"));
            status.setRollbackOnly();
        });

        // Assert
        assertThat(toTasker.poll(1, TimeUnit.SECONDS)).isNull();
        assertThat(messageRepository.count()).isZero();
    }

    @Test
    @DisplayName("Subscribing to anything but your own queues closes the connection")
    void subscribe_isRefused_forQueuesOfOthers() throws Exception {
        // Arrange
        StompSession session = connect("Bearer " + client.token());

        // Act
        session.subscribe("/queue/messages", frameHandler(new LinkedBlockingQueue<>()));

        // Assert
        await().atMost(Duration.ofSeconds(5)).until(() -> !session.isConnected());
    }

    private BlockingQueue<Map<String, Object>> listen(AuthResponse user, String destination) throws Exception {
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        StompSession session = connect("Bearer " + user.token());
        session.subscribe(destination, frameHandler(received));

        await().atMost(Duration.ofSeconds(5)).until(() -> isSubscribed(user.userId(), destination));
        return received;
    }

    private boolean isSubscribed(UUID userId, String destination) {
        var user = userRegistry.getUser(userId.toString());
        return user != null && user.getSessions().stream()
                .flatMap(session -> session.getSubscriptions().stream())
                .anyMatch(subscription -> subscription.getDestination().equals(destination));
    }

    private StompSession connect(String authorization) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", authorization);

        StompSession session = stompClient
                .connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
                        new StompSessionHandlerAdapter() {
                        })
                .get(5, TimeUnit.SECONDS);
        sessions.add(session);
        return session;
    }

    private static StompFrameHandler frameHandler(BlockingQueue<Map<String, Object>> received) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((Map<String, Object>) payload);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> message(Map<String, Object> event) {
        return (Map<String, Object>) event.get("message");
    }

    private UUID openConversation() {
        UUID offerId = offerService.submitOffer(publishedTask(), tasker.userId(),
                new CreateOfferRequest(new BigDecimal("40"), null)).id();
        return conversationOf(offerId);
    }

    private UUID conversationOf(UUID offerId) {
        Offer offer = offerRepository.findById(offerId).orElseThrow();
        return conversationRepository.findByOffer(offer).map(Conversation::getId).orElseThrow();
    }

    private UUID publishedTask() {
        UUID taskId = taskService.createTask(client.userId(), new CreateTaskRequest(
                "Fix the tap", "It drips",
                categoryRepository.findAll().getFirst().getId(),
                municipalityRepository.findAll().getFirst().getId(),
                new BigDecimal("50"))).id();
        taskService.publishTask(taskId, client.userId());
        return taskId;
    }

    private AuthResponse register(String email) {
        return authService.register(new RegisterRequest(email, "password123", "Live", "User", null));
    }
}
