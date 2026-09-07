package ba.tfb.tasknest.config;

import ba.tfb.tasknest.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AccountStatusUserDetailsChecker;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsChecker;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

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
                                           JwtAuthenticationFilter jwtAuthenticationFilter)
            throws Exception {
        http
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
                        .requestMatchers("/actuator/health").permitAll()
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
                // Bez ovoga Spring koristi Http403ForbiddenEntryPoint, pa neprijavljen
                // zahtjev dobije 403. Za JWT API je 401 tacan odgovor - klijent po
                // njemu zna da treba da se prijavi, dok 403 znaci "prijavljen, ali ne smijes".
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}