package com.ecommerce.aicustomerservice.config;

import com.ecommerce.aicustomerservice.security.JwtAuthenticationFilter;
import com.ecommerce.aicustomerservice.security.JwtService;
import com.ecommerce.aicustomerservice.security.RestAccessDeniedHandler;
import com.ecommerce.aicustomerservice.security.RestAuthenticationEntryPoint;
import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless JWT security. AI4's two GET endpoints (list sessions, view one session's messages)
 * are Admin-only; POST .../messages (AI1/AI2) and POST .../escalate (AI3) just require
 * authentication — ownership of a session is a service-layer check (404, never 403, to avoid
 * leaking session existence — see the user-stories doc's Session Lifecycle note), not something a
 * static Security path rule can express.
 *
 * <p><b>ERROR dispatch.</b> {@code /error} and the {@code ERROR} dispatcher type are permitted on
 * purpose. Without those rules any unhandled exception (a failing model call, for instance) is
 * forwarded to {@code /error}, that internal dispatch is evaluated by this chain as an anonymous
 * request — {@link JwtAuthenticationFilter} extends {@code OncePerRequestFilter} and does not run
 * on ERROR dispatches — and the entry point answers 401. Every model failure then looked like a bad
 * token. Model failures are mapped explicitly in {@code exception.GlobalExceptionHandler}.
 */
@Configuration
public class SecurityConfig {

    private final JwtService jwtService;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(JwtService jwtService, RestAuthenticationEntryPoint authenticationEntryPoint,
                           RestAccessDeniedHandler accessDeniedHandler) {
        this.jwtService = jwtService;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/ai-chat/sessions").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/ai-chat/sessions/*/messages").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** Permits the local static demo UI to call the authenticated chat API. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("http://localhost:*", "http://127.0.0.1:*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
