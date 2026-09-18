package com.barberia.citas.config;

import jakarta.servlet.http.HttpServletResponse;
import com.barberia.citas.repositorio.UsuarioRepository;
import com.barberia.citas.seguridad.LimitePeticionesPublicasFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   CorsConfigurationSource corsConfigurationSource,
                                                   LimitePeticionesPublicasFilter limitePeticiones) throws Exception {
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(csrfRequestHandler)
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(sessionFixation -> sessionFixation.migrateSession())
                )
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'self'; " +
                                "script-src 'self' 'unsafe-inline' https://cdn.tailwindcss.com; " +
                                "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; " +
                                "font-src 'self' data: https://fonts.gstatic.com; " +
                                "img-src 'self' data: https:; connect-src 'self'; " +
                                "object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'"
                        ))
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .frameOptions(frame -> frame.deny())
                        .addHeaderWriter((request, response) -> response.setHeader(
                                "Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()"))
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/", "/index.html", "/login.html", "/favicon.ico",
                                "/css/**", "/js/**", "/img/**", "/media/**", "/error"
                        ).permitAll()
                        .requestMatchers(
                                "/api/auth/registro", "/api/auth/login", "/api/auth/csrf"
                        ).permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/citas/servicios",
                                "/api/citas/barberos",
                                "/api/citas/disponibilidad",
                                "/api/citas/servicios/populares",
                                "/api/citas/mis/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/citas").permitAll()
                        .requestMatchers("/panel.html").hasRole("ADMIN")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/citas").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/citas/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/citas/servicios").hasRole("ADMIN")
                        .requestMatchers("/api/auth/me", "/api/auth/logout").authenticated()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            if (request.getRequestURI().endsWith("/panel.html")) {
                                response.sendRedirect("/login.html?redirect=panel");
                            } else {
                                escribirError(response, HttpServletResponse.SC_UNAUTHORIZED, "No autenticado");
                            }
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            if (request.getRequestURI().endsWith("/panel.html")) {
                                response.sendRedirect("/index.html");
                            } else {
                                escribirError(response, HttpServletResponse.SC_FORBIDDEN, "Acceso denegado");
                            }
                        })
                )
                .addFilterAfter(limitePeticiones, CsrfFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public UserDetailsService userDetailsService(UsuarioRepository usuarios) {
        return email -> usuarios.findByEmailIgnoreCase(email)
                .filter(usuario -> usuario.getPassword() != null)
                .map(usuario -> User.withUsername(usuario.getEmail())
                        .password(usuario.getPassword())
                        .roles(usuario.getRol().name())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Credenciales incorrectas"));
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.security.allowed-origins:}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN", "Idempotency-Key"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    private static void escribirError(HttpServletResponse response, int status, String mensaje) throws java.io.IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"status\":" + status + ",\"mensaje\":\"" + mensaje + "\",\"errores\":{}}");
    }
}
