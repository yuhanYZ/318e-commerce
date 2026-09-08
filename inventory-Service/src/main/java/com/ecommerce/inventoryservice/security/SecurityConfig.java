package com.ecommerce.inventoryservice.security;

import com.ecommerce.inventoryservice.dto.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.io.IOException;
import java.time.Instant;

/**
 * Stateless JWT-based security configuration. Per specs/openapi-contract.yaml:
 * I1 (GET /inventory/{productId}) declares no bearerAuth requirement -
 * Customers (directly or via Product Service) check stock without
 * authenticating; I3 (POST /inventory/{productId}/adjust) and I4
 * (GET /inventory/alerts) both declare bearerAuth and are Admin-only per
 * their summaries.
 * <p>
 * Matcher ORDER matters here (Spring Security uses the first matching rule):
 * the exact-path "/inventory/alerts" rule must come before the single-segment
 * "/inventory/*" pattern below it, or "GET /inventory/alerts" would
 * incorrectly fall through to the permitAll wildcard instead of the
 * ADMIN-only rule.
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/h2-console/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/inventory/alerts").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/inventory/*/adjust").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/inventory/*").permitAll()
                        .anyRequest().authenticated()
                )
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((request, response, authException) ->
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        "UNAUTHORIZED", "Missing or invalid authentication token"))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeError(response, HttpServletResponse.SC_FORBIDDEN,
                                        "FORBIDDEN", "Access denied"))
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        ErrorResponse body = new ErrorResponse(code, message, Instant.now().toString());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
