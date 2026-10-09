package ba.tfb.tasknest;

import ba.tfb.tasknest.geo.OpenRouteServiceGeocoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class TaskNestApplicationTests extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
    }

    @Test
    @DisplayName("The real geocoder can be built from the application context, although other tests replace it")
    void realGeocoder_canBeCreated() {
        OpenRouteServiceGeocoder geocoder = context.getAutowireCapableBeanFactory()
                .createBean(OpenRouteServiceGeocoder.class);

        assertThat(geocoder).isNotNull();
    }
}
