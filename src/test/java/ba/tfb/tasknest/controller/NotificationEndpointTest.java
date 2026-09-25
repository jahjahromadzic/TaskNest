package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.auth.AuthResponse;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.entity.Notification;
import ba.tfb.tasknest.entity.enums.NotificationType;
import ba.tfb.tasknest.repository.NotificationRepository;
import ba.tfb.tasknest.repository.RefreshTokenRepository;
import ba.tfb.tasknest.repository.UserRepository;
import ba.tfb.tasknest.service.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class NotificationEndpointTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    private AuthResponse owner;
    private AuthResponse stranger;

    @BeforeEach
    void setUp() {
        owner = register("notif.owner@test.ba");
        stranger = register("notif.stranger@test.ba");
    }

    @AfterEach
    void tearDown() {
        notificationRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("A user sees only their own notifications")
    void myNotifications_returnsOnlyOwnNotifications() throws Exception {
        persistNotification(owner.userId(), "Za mene");
        persistNotification(stranger.userId(), "Za nekog drugog");

        mockMvc.perform(get("/api/notifications").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].content").value("Za mene"))
                .andExpect(jsonPath("$.content[0].type").value("NEW_TASK_IN_AREA"))
                .andExpect(jsonPath("$.content[0].read").value(false));
    }

    @Test
    @DisplayName("The notification list uses the shared paged shape")
    void myNotifications_usesTheSharedPagedShape() throws Exception {
        mockMvc.perform(get("/api/notifications").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.pageable").doesNotExist());
    }

    @Test
    @DisplayName("An unknown sort field is ignored instead of failing with 500")
    void myNotifications_ignoresUnknownSortField() throws Exception {
        // Ranije je ovo prolazilo do Spring Date i vracalo 500. Redoslijed je
        // fiksan (najnovije prvo), pa se sort iz zahtjeva ne prenosi dalje.
        persistNotification(owner.userId(), "Prva");

        mockMvc.perform(get("/api/notifications")
                        .param("sort", "bilosta")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("The unread count reflects only unread notifications of the caller")
    void unreadCount_countsOnlyOwnUnread() throws Exception {
        persistNotification(owner.userId(), "Prva");
        persistNotification(owner.userId(), "Druga");
        persistNotification(stranger.userId(), "Tudja");

        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
    }

    @Test
    @DisplayName("Marking a notification as read lowers the unread count")
    void markAsRead_marksNotificationAndLowersUnreadCount() throws Exception {
        UUID id = persistNotification(owner.userId(), "Prva");
        persistNotification(owner.userId(), "Druga");

        mockMvc.perform(post("/api/notifications/" + id + "/read").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));

        mockMvc.perform(get("/api/notifications/unread-count").header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.count").value(1));

        assertThat(notificationRepository.findById(id).orElseThrow().isRead()).isTrue();
    }

    @Test
    @DisplayName("Marking an already read notification again is not an error")
    void markAsRead_isIdempotent() throws Exception {
        UUID id = persistNotification(owner.userId(), "Prva");

        mockMvc.perform(post("/api/notifications/" + id + "/read").header("Authorization", bearer(owner)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/notifications/" + id + "/read").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    @DisplayName("A user cannot mark someone else's notification as read")
    void markAsRead_isForbidden_whenNotificationBelongsToAnotherUser() throws Exception {
        UUID id = persistNotification(owner.userId(), "Za mene");

        mockMvc.perform(post("/api/notifications/" + id + "/read").header("Authorization", bearer(stranger)))
                .andExpect(status().isForbidden());

        assertThat(notificationRepository.findById(id).orElseThrow().isRead())
                .as("tudji zahtjev ne smije promijeniti stanje")
                .isFalse();
    }

    @Test
    @DisplayName("Marking a notification that does not exist is 404")
    void markAsRead_isNotFound_whenNotificationDoesNotExist() throws Exception {
        mockMvc.perform(post("/api/notifications/" + UUID.randomUUID() + "/read")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Notifications require authentication")
    void myNotifications_isUnauthorised_whenAnonymous() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- helpers ----------

    private String bearer(AuthResponse user) {
        return "Bearer " + user.token();
    }

    private UUID persistNotification(UUID recipientId, String content) {
        Notification notification = new Notification();
        notification.setRecipient(userRepository.getReferenceById(recipientId));
        notification.setType(NotificationType.NEW_TASK_IN_AREA);
        notification.setRelatedEntityId(UUID.randomUUID());
        notification.setContent(content);

        return notificationRepository.saveAndFlush(notification).getId();
    }

    private AuthResponse register(String email) {
        return authService.register(new RegisterRequest(email, "password123", "Test", "User", null));
    }
}
