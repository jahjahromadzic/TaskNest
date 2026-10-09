package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.auth.AuthResponse;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.dto.offer.CreateOfferRequest;
import ba.tfb.tasknest.dto.task.CreateTaskRequest;
import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.repository.CategoryRepository;
import ba.tfb.tasknest.repository.MunicipalityRepository;
import ba.tfb.tasknest.repository.TaskPhotoRepository;
import ba.tfb.tasknest.service.AuthService;
import ba.tfb.tasknest.service.OfferService;
import ba.tfb.tasknest.service.TaskPhotoService;
import ba.tfb.tasknest.service.TaskService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TaskPhotoEndpointTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private TaskService taskService;
    @Autowired private OfferService offerService;
    @Autowired private TaskPhotoService taskPhotoService;
    @Autowired private TaskPhotoRepository photoRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MunicipalityRepository municipalityRepository;

    private AuthResponse client;
    private AuthResponse stranger;
    private Category category;
    private Municipality municipality;

    @BeforeEach
    void setUp() {
        client = register("photo.client@test.ba");
        stranger = register("photo.stranger@test.ba");
        category = categoryRepository.findAll().stream().filter(Category::isActive).findFirst().orElseThrow();
        municipality = municipalityRepository.findAll().getFirst();
    }

    @Test
    @DisplayName("The owner adds a photo; it shows on the task, as the cover in the list, and is served to anyone")
    void uploadedPhotoShowsEverywhere() throws Exception {
        // Arrange
        UUID taskId = publishedTask();

        // Act
        String body = upload(taskId, client, jpeg(800, 600))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.width").value(800))
                .andExpect(jsonPath("$.height").value(600))
                .andReturn().getResponse().getContentAsString();
        String photoId = JsonPath.read(body, "$.id");

        // Assert
        mockMvc.perform(get("/api/tasks/{id}", taskId))
                .andExpect(jsonPath("$.photos[0].id").value(photoId))
                .andExpect(jsonPath("$.photos[0].url").value("/api/photos/" + photoId));
        mockMvc.perform(get("/api/tasks"))
                .andExpect(jsonPath("$.content[0].coverPhotoId").value(photoId));
        byte[] served = mockMvc.perform(get("/api/photos/{id}", photoId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(header().string("Cache-Control", containsString("immutable")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(ImageIO.read(new ByteArrayInputStream(served)).getWidth()).isEqualTo(800);
    }

    @Test
    @DisplayName("Hidden metadata such as the GPS location is removed from the stored photo")
    void metadataIsStripped() throws Exception {
        // Arrange
        UUID taskId = publishedTask();
        byte[] withExif = withExifSegment(jpeg(400, 300), "GPS-43.8563-18.4131");

        // Act
        String body = upload(taskId, client, withExif).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Assert
        byte[] served = mockMvc.perform(get("/api/photos/{id}", (String) JsonPath.read(body, "$.id")))
                .andReturn().getResponse().getContentAsByteArray();
        String asText = new String(served, StandardCharsets.ISO_8859_1);
        assertThat(asText).doesNotContain("Exif").doesNotContain("GPS-43.8563");
    }

    @Test
    @DisplayName("A large photo is scaled down so its longest side is 1920 pixels")
    void largePhotoIsScaledDown() throws Exception {
        upload(publishedTask(), client, jpeg(3000, 1500))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.width").value(1920))
                .andExpect(jsonPath("$.height").value(960));
    }

    @Test
    @DisplayName("Files that are not real JPEG or PNG images are refused, whatever their name says")
    void nonImagesAreRefused() throws Exception {
        UUID taskId = publishedTask();

        upload(taskId, client, "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Only JPEG and PNG photos are accepted"));
        upload(taskId, client, new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4})
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The photo could not be read; it may be damaged"));
        assertThat(photoRepository.count()).isZero();
    }

    @Test
    @DisplayName("A photo with an absurd number of pixels is refused before it is decoded")
    void pixelBombIsRefused() throws Exception {
        upload(publishedTask(), client, pngWithClaimedSize(20_000, 20_000))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The photo has too many pixels; use one under 40 megapixels"));
    }

    @Test
    @DisplayName("A task holds at most five photos")
    void sixthPhotoIsRefused() throws Exception {
        UUID taskId = publishedTask();
        for (int i = 0; i < 5; i++) {
            upload(taskId, client, png(50, 50)).andExpect(status().isCreated());
        }

        upload(taskId, client, png(50, 50))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("A task can have at most 5 photos"));
    }

    @Test
    @DisplayName("Two uploads at the same moment cannot push a task over five photos")
    void parallelUploadsRespectTheLimit() throws Exception {
        // Arrange
        UUID taskId = publishedTask();
        for (int i = 0; i < 4; i++) {
            taskPhotoService.addPhoto(taskId, client.userId(), png(40, 40));
        }
        byte[] photo = png(40, 40);
        Callable<Boolean> attempt = () -> {
            try {
                taskPhotoService.addPhoto(taskId, client.userId(), photo);
                return true;
            } catch (RuntimeException e) {
                return false;
            }
        };

        // Act
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<Boolean>> results = new ArrayList<>(executor.invokeAll(List.of(attempt, attempt)));
        executor.shutdown();

        // Assert
        assertThat(results.stream().filter(this::succeeded).count()).isEqualTo(1);
        assertThat(photoRepository.countByTaskId(taskId)).isEqualTo(5);
    }

    @Test
    @DisplayName("Only the owner can add or remove photos, and a visitor must sign in first")
    void onlyTheOwnerChangesPhotos() throws Exception {
        UUID taskId = publishedTask();

        upload(taskId, stranger, png(50, 50)).andExpect(status().isForbidden());
        mockMvc.perform(multipart("/api/tasks/{id}/photos", taskId).file(file(png(50, 50))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Deleting a photo removes the file and the next photo becomes the cover")
    void deletingMovesTheCover() throws Exception {
        // Arrange
        UUID taskId = publishedTask();
        String first = JsonPath.read(upload(taskId, client, png(60, 60)).andReturn().getResponse().getContentAsString(), "$.id");
        String second = JsonPath.read(upload(taskId, client, png(70, 70)).andReturn().getResponse().getContentAsString(), "$.id");

        // Act
        mockMvc.perform(delete("/api/tasks/{taskId}/photos/{photoId}", taskId, first).header("Authorization", bearer(client)))
                .andExpect(status().isNoContent());

        // Assert
        mockMvc.perform(get("/api/photos/{id}", first)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/tasks")).andExpect(jsonPath("$.content[0].coverPhotoId").value(second));
    }

    @Test
    @DisplayName("Photos are frozen once someone is hired, and hidden when a moderator removes the task")
    void photosFollowTheTaskLifecycle() throws Exception {
        // Arrange
        UUID hired = publishedTask();
        UUID removed = publishedTask();
        upload(removed, client, png(50, 50));
        String removedPhoto = photoRepository.findByTaskIdOrderByPositionAsc(removed).getFirst().getId().toString();
        AuthResponse tasker = register("photo.tasker@test.ba");
        authService.activateTaskerRole(tasker.userId());
        UUID offerId = offerService.submitOffer(hired, tasker.userId(), new CreateOfferRequest(new BigDecimal("40"), "Mogu sutra")).id();
        offerService.acceptOffer(offerId, client.userId());

        // Act
        taskService.removeTask(removed, "Spam");

        // Assert
        upload(hired, client, png(50, 50))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Photos can only be changed while the task is a draft or open for offers"));
        mockMvc.perform(get("/api/photos/{id}", removedPhoto)).andExpect(status().isNotFound());
    }

    private boolean succeeded(Future<Boolean> future) {
        try {
            return future.get();
        } catch (Exception e) {
            return false;
        }
    }

    private ResultActions upload(UUID taskId, AuthResponse who, byte[] bytes) throws Exception {
        return mockMvc.perform(multipart("/api/tasks/{id}/photos", taskId)
                .file(file(bytes))
                .header("Authorization", bearer(who)));
    }

    private static MockMultipartFile file(byte[] bytes) {
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", bytes);
    }

    private UUID publishedTask() {
        UUID taskId = taskService.createTask(client.userId(), new CreateTaskRequest(
                "Curi slavina", "Kapa i kad je zatvorena", category.getId(), municipality.getId(),
                "Zmaja od Bosne 12", new BigDecimal("60"))).id();
        taskService.publishTask(taskId, client.userId());
        return taskId;
    }

    private static byte[] jpeg(int width, int height) throws IOException {
        return encode(width, height, BufferedImage.TYPE_INT_RGB, "jpeg");
    }

    private static byte[] png(int width, int height) throws IOException {
        return encode(width, height, BufferedImage.TYPE_INT_ARGB, "png");
    }

    private static byte[] encode(int width, int height, int type, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, type);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(new Color(15, 118, 110));
        graphics.fillRect(0, 0, width, height / 2);
        graphics.dispose();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        ImageIO.write(image, format, bytes);
        return bytes.toByteArray();
    }

    private static byte[] withExifSegment(byte[] jpeg, String secret) {
        byte[] payload = ("Exif\0\0" + secret).getBytes(StandardCharsets.ISO_8859_1);
        ByteBuffer segment = ByteBuffer.allocate(4 + payload.length);
        segment.put((byte) 0xFF).put((byte) 0xE1).putShort((short) (payload.length + 2)).put(payload);
        ByteBuffer result = ByteBuffer.allocate(jpeg.length + segment.capacity());
        result.put(jpeg, 0, 2).put(segment.array()).put(jpeg, 2, jpeg.length - 2);
        return result.array();
    }

    private static byte[] pngWithClaimedSize(int width, int height) throws IOException {
        byte[] png = png(10, 10);
        ByteBuffer buffer = ByteBuffer.wrap(png);
        buffer.putInt(16, width).putInt(20, height);
        CRC32 crc = new CRC32();
        crc.update(png, 12, 17);
        buffer.putInt(29, (int) crc.getValue());
        return png;
    }

    private AuthResponse register(String email) {
        return authService.register(new RegisterRequest(email, "password123", "Test", "User", null));
    }

    private static String bearer(AuthResponse auth) {
        return "Bearer " + auth.token();
    }
}
