package mx.gob.seph.smp.demo.config;

import java.io.IOException;
import java.util.List;

import mx.gob.seph.smp.demo.api.ApiResponse;
import mx.gob.seph.smp.demo.auth.JwtAuthenticationFilter;
import mx.gob.seph.smp.demo.auth.JwtSessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class SecurityConfig {
    /** Headers con los que el front manda ids y datos de cuenta (así los recibe el back real). */
    private static final List<String> ALLOWED_HEADERS = List.of(
            "Authorization", "Content-Type", "userId", "roleId", "moduleId", "token", "ts", "password", "username");

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtSessionService jwtSessionService,
            ObjectMapper objectMapper) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(
                        (request, response, exception) -> writeUnauthorized(request, response, objectMapper)))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/login").permitAll()
                        // Recuperación de contraseña: sin sesión
                        .requestMatchers(HttpMethod.POST, "/users/sendChangePassword", "/users/changePasswordRecovery")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/users/validatePasswordRecovery").permitAll()
                        .requestMatchers("/users/**", "/roles/**", "/modules/**", "/icons/**", "/fup/**",
                                "/consultas/**", "/empleados/**").authenticated()
                        .anyRequest().permitAll())
                .addFilterBefore(new JwtAuthenticationFilter(jwtSessionService), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${demo.cors.allowed-origin}") String allowedOrigin) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(ALLOWED_HEADERS);
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response, ObjectMapper objectMapper)
            throws IOException {
        Object reason = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE);
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        // Sin `message` el front avisa "Sesión expirada"; con él, "Sesión terminada"
        objectMapper.writeValue(response.getOutputStream(),
                ApiResponse.failure(reason == null ? null : reason.toString()));
    }
}
