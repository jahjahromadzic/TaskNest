package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.AbstractIntegrationTest;
import ba.tfb.tasknest.dto.auth.AuthResponse;
import ba.tfb.tasknest.dto.auth.RegisterRequest;
import ba.tfb.tasknest.entity.Municipality;
import ba.tfb.tasknest.geo.GeoPoint;
import ba.tfb.tasknest.repository.MunicipalityRepository;
import ba.tfb.tasknest.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.ResourceAccessException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class LocationEndpointTest extends AbstractIntegrationTest {

    private static final GeoPoint KOLODVORSKA = new GeoPoint(new BigDecimal("43.856426"), new BigDecimal("18.389188"));

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private MunicipalityRepository municipalityRepository;

    private AuthResponse client;
    private Municipality novoSarajevo;

    @BeforeEach
    void setUp() {
        client = authService.register(new RegisterRequest("lookup.client@test.ba", "password123", "Amra", "Hodžić", null));
        novoSarajevo = municipalityRepository.findAll().stream()
                .filter(candidate -> candidate.getName().equals("Novo Sarajevo"))
                .findFirst().orElseThrow();
    }

    @Test
    @DisplayName("A found address comes back as its own point")
    void lookup_returnsTheAddress_whenItIsFound() throws Exception {
        when(geocoder.geocode("Kolodvorska 12", "Novo Sarajevo")).thenReturn(Optional.of(KOLODVORSKA));

        lookup("  Kolodvorska 12 ", novoSarajevo.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.latitude").value(43.856426))
                .andExpect(jsonPath("$.longitude").value(18.389188));
    }

    @Test
    @DisplayName("An address the map does not know starts the pin at the seat of the municipality")
    void lookup_returnsTheMunicipalitySeat_whenTheAddressIsNotFound() throws Exception {
        when(geocoder.geocode(anyString(), anyString())).thenReturn(Optional.empty());

        expectSeat(lookup("Zaseok Brdo bb", novoSarajevo.getId()));
    }

    @Test
    @DisplayName("When the map service is down the pin still starts at the seat of the municipality")
    void lookup_returnsTheMunicipalitySeat_whenTheServiceIsDown() throws Exception {
        when(geocoder.geocode(anyString(), anyString())).thenThrow(new ResourceAccessException("Connection refused"));

        expectSeat(lookup("Kolodvorska 12", novoSarajevo.getId()));
    }

    @Test
    @DisplayName("Without an address the seat of the municipality is returned without asking the map service")
    void lookup_withoutAnAddress_returnsTheSeat() throws Exception {
        expectSeat(lookup("   ", novoSarajevo.getId()));
        expectSeat(mockMvc.perform(get("/api/geo/lookup")
                .param("municipalityId", novoSarajevo.getId().toString())
                .header("Authorization", "Bearer " + client.token())));

        verify(geocoder, never()).geocode(anyString(), anyString());
    }

    @Test
    @DisplayName("An unknown municipality is reported as not found")
    void lookup_rejectsAnUnknownMunicipality() throws Exception {
        lookup("Kolodvorska 12", UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("An address longer than 200 characters is refused")
    void lookup_refusesAVeryLongAddress() throws Exception {
        lookup("a".repeat(201), novoSarajevo.getId()).andExpect(status().isBadRequest());

        verify(geocoder, never()).geocode(anyString(), anyString());
    }

    @Test
    @DisplayName("Visitors who are not signed in cannot use the lookup")
    void lookup_requiresSignIn() throws Exception {
        mockMvc.perform(get("/api/geo/lookup")
                        .param("address", "Kolodvorska 12")
                        .param("municipalityId", novoSarajevo.getId().toString()))
                .andExpect(status().isUnauthorized());

        verify(geocoder, never()).geocode(anyString(), anyString());
    }

    private ResultActions lookup(String address, UUID municipalityId) throws Exception {
        return mockMvc.perform(get("/api/geo/lookup")
                .param("address", address)
                .param("municipalityId", municipalityId.toString())
                .header("Authorization", "Bearer " + client.token()));
    }

    private void expectSeat(ResultActions result) throws Exception {
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(false))
                .andExpect(jsonPath("$.latitude").value(novoSarajevo.getLatitude().doubleValue()))
                .andExpect(jsonPath("$.longitude").value(novoSarajevo.getLongitude().doubleValue()));
    }
}
