package com.ecommerce.aicustomerservice.config;

import com.ecommerce.aicustomerservice.security.JwtAuthenticationFilter;
import com.ecommerce.aicustomerservice.security.JwtService;
import com.ecommerce.aicustomerservice.security.RestAccessDeniedHandler;
import com.ecommerce.aicustomerservice.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT security. AI4's two GET endpoints (list sessions, view one session's messages)
 * are Admin-only; POST .../messages (AI1/AI2) and POST .../escalate (AI3) just require
 * authentication — ownership of a session is a service-layer check (404, never 403, to avoid
 * leaking session existence — see the user-stories doc's Session Lifecycle note), not something a
 * static Security path rule can express.
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
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/api/v1/ai-chat/sessions").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/ai-chat/sessions/*/messages").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtService), UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}
