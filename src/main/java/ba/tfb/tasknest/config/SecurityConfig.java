package ba.tfb.tasknest.config;

import ba.tfb.tasknest.security.JwtAuthenticationFilter;
import ba.tfb.tasknest.security.ProblemDetailAccessDeniedHandler;
import ba.tfb.tasknest.security.ProblemDetailAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Jedna politika statusa naloga za obje putanje - login i JWT filter.
     * Baca DisabledException za DEACTIVATED, LockedException za SUSPENDED.
     */
    @Bean
    public UserDetailsChecker accountStatusChecker() {
        return new AccountStatusUserDetailsChecker();
    }

    /**
     * Filter stize kao parametar, ne kroz konstruktor: on zavisi od
     * accountStatusChecker() koji je definisan ovdje, pa bi konstruktorska
     * injekcija napravila ciklus SecurityConfig -> filter -> SecurityConfig.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           JwtAuthenticationFilter jwtAuthenticationFilter,
                                           ProblemDetailAuthenticationEntryPoint entryPoint,
                                           ProblemDetailAccessDeniedHandler accessDeniedHandler)
            throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Eksplicitne putanje, ne /api/auth/** - da buduci endpointi
                        // pod istim prefiksom ne postanu javni slucajno.
                        .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        // Refresh i logout su javni jer je sam refresh token kredencijal:
                        // access token je u trenutku osvjezavanja po pravilu vec istekao.
                        .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/municipalities/**").permitAll()
                        // Reputacija je javna: prosjek na profilu taskera nema svrhe
                        // ako se ocjene koje ga cine ne mogu procitati prije dogovora.
                        .requestMatchers(HttpMethod.GET, "/api/users/*/reviews").permitAll()
                        // MORA prije /api/tasks/* - matcheri se evaluiraju po redu.
                        // Inace ih wildcard propusti kao javne, pa @PreAuthorize
                        // anonimnom korisniku vrati 403 ("nemas pravo") umjesto
                        // 401 ("prijavi se"), po cemu klijent bira sta da uradi.
                        .requestMatchers(HttpMethod.GET,
                                "/api/tasks/matching",
                                "/api/tasks/mine",
                                "/api/tasks/assigned").authenticated()
                        // Javna lista oglasa i pojedinacni oglas. Jedna zvjezdica hvata
                        // samo /api/tasks/{id}, ne i /api/tasks/{id}/offers - ponude
                        // vidi samo vlasnik taska.
                        .requestMatchers(HttpMethod.GET, "/api/tasks").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/tasks/*").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // sendError() radi interni forward na /error, koji ponovo prolazi
                        // kroz ovaj lanac - ali bez Authorization headera, pa bude anoniman.
                        // Da /error nije javan, entry point bi pregazio svaki 403 u 401.
                        .requestMatchers("/error").permitAll()
                        // Springdoc koristi vise putanja: swagger-ui.html preusmjerava
                        // na /swagger-ui/index.html, a stranica onda povlaci definiciju
                        // sa /v3/api-docs. Sve moraju biti javne.
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml"
                        ).permitAll()
                        .anyRequest().authenticated())
                // Dva razlicita ishoda, dva razlicita handlera:
                //   neautentikovan          -> 401, klijent salje na login
                //   autentikovan bez prava  -> 403, klijent prikazuje poruku
                // Oba pisu ProblemDetail kroz isti resolver kao ostale greske.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS za frontend koji radi na drugom originu (Angular dev server na :4200).
     * <p>
     * Bez ovoga preglednik odbija svaki odgovor prije nego frontend ista vidi.
     * Mora biti u Spring Security lancu (cors() iznad), ne samo kao WebMvc
     * konfiguracija: preflight OPTIONS nema token, pa bi ga filter odbio sa 401
     * prije nego sto MVC dobije priliku da odgovori.
     * <p>
     * allowCredentials=false jer token putuje u Authorization zaglavlju, ne u
     * kolacicu - a uz credentials bi origin morao biti eksplicitan, bez "*".
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}