package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.auth.AuthResponse;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.entity.Category;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.entity.Task;
import ba.tfb.tasknest.geo.GeoPoint;
import ba.tfb.tasknest.repository.CategoryRepository;
import ba.tfb.tasknest.repository.MunicipalityRepository;
import ba.tfb.tasknest.repository.TaskRepository;
import ba.tfb.tasknest.service.AuthService;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class TaskAddressEndpointTest extends AbstractIntegrationTest {

    private static final GeoPoint ILIDZA = new GeoPoint(new BigDecimal("43.829900"), new BigDecimal("18.310500"));

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private MunicipalityRepository municipalityRepository;
    @Autowired private TaskRepository taskRepository;

    private AuthResponse client;
    private Category category;
    private Municipality centar;
    private Municipality ilidza;

    @BeforeEach
    void setUp() {
        client = authService.register(
                new RegisterRequest("address.client@test.ba", "password123", "Amra", "Hodžić", null));
        category = categoryRepository.findAll().stream().filter(Category::isActive).findFirst().orElseThrow();
        centar = named("Centar Sarajevo");
        ilidza = named("Ilidža");
    }

    @Test
    @DisplayName("A new task keeps its address and the coordinates the geocoder found")
    void create_storesAddressAndCoordinates() throws Exception {
        UUID taskId = create(body("Popravka slavine", centar, "  Zmaja od Bosne 12  "))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8)
                .transform(json -> UUID.fromString(JsonPath.read(json, "$.id")));

        Task task = taskRepository.findById(taskId).orElseThrow();
        assertThat(task.getAddressLine()).isEqualTo("Zmaja od Bosne 12");
        assertThat(task.getLatitude()).isEqualByComparingTo(SARAJEVO.latitude());
        assertThat(task.getLongitude()).isEqualByComparingTo(SARAJEVO.longitude());
        verify(geocoder).geocode("Zmaja od Bosne 12", "Centar Sarajevo");
    }

    @Test
    @DisplayName("An address the geocoder cannot find is refused and no task is created")
    void create_refusesAnUnknownAddress() throws Exception {
        when(geocoder.geocode(anyString(), anyString())).thenReturn(Optional.empty());

        create(body("Popravka slavine", centar, "kod velike džamije"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The address could not be found"));

        assertThat(taskRepository.count()).isZero();
    }

    @Test
    @DisplayName("A task without an address is refused before the geocoder is asked")
    void create_requiresAnAddress() throws Exception {
        create(body("Popravka slavine", centar, " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("address")));

        verify(geocoder, never()).geocode(anyString(), anyString());
    }

    @Test
    @DisplayName("Editing only the title keeps the coordinates without asking the geocoder again")
    void edit_withTheSamePlace_doesNotGeocodeAgain() throws Exception {
        UUID taskId = createdTask();

        edit(taskId, body("Popravka slavine i česme", centar, "Zmaja od Bosne 12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Popravka slavine i česme"));

        verify(geocoder, times(1)).geocode(anyString(), anyString());
    }

    @Test
    @DisplayName("Moving the task to another address or municipality looks it up again")
    void edit_withANewPlace_geocodesAgain() throws Exception {
        UUID taskId = createdTask();
        when(geocoder.geocode("Butmirska cesta 14", "Ilidža")).thenReturn(Optional.of(ILIDZA));

        edit(taskId, body("Popravka slavine", ilidza, "Butmirska cesta 14"))
                .andExpect(status().isOk());

        Task task = taskRepository.findById(taskId).orElseThrow();
        assertThat(task.getAddressLine()).isEqualTo("Butmirska cesta 14");
        assertThat(task.getLatitude()).isEqualByComparingTo(ILIDZA.latitude());
        assertThat(task.getLongitude()).isEqualByComparingTo(ILIDZA.longitude());
    }

    @Test
    @DisplayName("A new address that cannot be found leaves the task as it was")
    void edit_withAnUnknownAddress_keepsTheOldPlace() throws Exception {
        UUID taskId = createdTask();
        when(geocoder.geocode("Nepostojeća 999", "Centar Sarajevo")).thenReturn(Optional.empty());

        edit(taskId, body("Novi naslov", centar, "Nepostojeća 999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The address could not be found"));

        Task task = taskRepository.findById(taskId).orElseThrow();
        assertThat(task.getTitle()).isEqualTo("Popravka slavine");
        assertThat(task.getAddressLine()).isEqualTo("Zmaja od Bosne 12");
        assertThat(task.getLatitude()).isEqualByComparingTo(SARAJEVO.latitude());
    }

    @Test
    @DisplayName("A pin confirmed on the map is kept as it is and the geocoder is not asked")
    void create_withAPin_keepsItWithoutTheGeocoder() throws Exception {
        UUID taskId = idOf(create(withPin("Popravka slavine", centar, "Zaseok bez imena", "43.8590004", "18.4120006"))
                .andExpect(status().isCreated()));

        Task task = taskRepository.findById(taskId).orElseThrow();
        assertThat(task.getAddressLine()).isEqualTo("Zaseok bez imena");
        assertThat(task.getLatitude()).isEqualByComparingTo("43.859000");
        assertThat(task.getLongitude()).isEqualByComparingTo("18.412001");
        verify(geocoder, never()).geocode(anyString(), anyString());
    }

    @Test
    @DisplayName("A pin far away from the chosen municipality is refused")
    void create_refusesAPinFarFromTheMunicipality() throws Exception {
        create(withPin("Popravka slavine", centar, "Zmaja od Bosne 12", "44.538000", "18.667000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The pin is too far from the chosen municipality"));

        assertThat(taskRepository.count()).isZero();
    }

    @Test
    @DisplayName("A pin needs both its latitude and its longitude")
    void create_refusesHalfAPin() throws Exception {
        create("""
                {"title": "Popravka", "categoryId": "%s", "municipalityId": "%s", "address": "Zmaja od Bosne 12",
                 "latitude": 43.85}
                """.formatted(category.getId(), centar.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("Send both the latitude and the longitude of the pin, or neither"));

        assertThat(taskRepository.count()).isZero();
    }

    @Test
    @DisplayName("Moving only the pin updates the coordinates without asking the geocoder")
    void edit_movingThePin_updatesTheCoordinates() throws Exception {
        UUID taskId = createdTask();

        edit(taskId, withPin("Popravka slavine", centar, "Zmaja od Bosne 12", "43.855100", "18.394200"))
                .andExpect(status().isOk());

        Task task = taskRepository.findById(taskId).orElseThrow();
        assertThat(task.getLatitude()).isEqualByComparingTo("43.855100");
        assertThat(task.getLongitude()).isEqualByComparingTo("18.394200");
        verify(geocoder, times(1)).geocode(anyString(), anyString());
    }

    private String withPin(String title, Municipality municipality, String address, String latitude, String longitude) {
        return """
                {"title": "%s", "categoryId": "%s", "municipalityId": "%s", "address": "%s",
                 "latitude": %s, "longitude": %s}
                """.formatted(title, category.getId(), municipality.getId(), address, latitude, longitude);
    }

    private static UUID idOf(ResultActions result) throws Exception {
        String json = result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return UUID.fromString(JsonPath.read(json, "$.id"));
    }

    private UUID createdTask() throws Exception {
        String json = create(body("Popravka slavine", centar, "Zmaja od Bosne 12"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return UUID.fromString(JsonPath.read(json, "$.id"));
    }

    private String body(String title, Municipality municipality, String address) {
        return """
                {"title": "%s", "categoryId": "%s", "municipalityId": "%s", "address": "%s"}
                """.formatted(title, category.getId(), municipality.getId(), address);
    }

    private ResultActions create(String json) throws Exception {
        return mockMvc.perform(post("/api/tasks")
                .header("Authorization", "Bearer " + client.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions edit(UUID taskId, String json) throws Exception {
        return mockMvc.perform(put("/api/tasks/" + taskId)
                .header("Authorization", "Bearer " + client.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private Municipality named(String name) {
        return municipalityRepository.findAll().stream()
                .filter(candidate -> candidate.getName().equals(name))
                .findFirst().orElseThrow();
    }
}
