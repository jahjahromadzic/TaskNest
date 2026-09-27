package ba.tfb.tasknest.controller;

import ba.tfb.tasknest.AbstractIntegrationTest;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class ReferenceDataEndpointTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("Every seeded category has its own slug, which the frontend uses to pick an icon")
    void categories_haveUniqueSlugs() throws Exception {
        String body = mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        List<String> slugs = JsonPath.read(body, "$[*].slug");

        assertThat(slugs).hasSize(14).doesNotContainNull().allMatch(slug -> slug.matches("[a-z-]+"));
        assertThat(new HashSet<>(slugs)).hasSameSizeAs(slugs);
    }

    @Test
    @DisplayName("Municipality names keep their local spelling")
    void municipalities_keepLocalSpelling() throws Exception {
        String body = mockMvc.perform(get("/api/municipalities"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        List<String> names = JsonPath.read(body, "$[*].name");

        assertThat(names).contains("Ilidža", "Vogošća", "Novi Grad Sarajevo");
    }
}
