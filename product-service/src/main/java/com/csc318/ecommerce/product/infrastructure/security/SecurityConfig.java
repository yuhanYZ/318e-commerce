package com.csc318.ecommerce.product.infrastructure.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.*;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfig {
    @Bean SecurityFilterChain securityFilterChain(HttpSecurity http,JwtAuthenticationFilter jwt)throws Exception{
        return http.csrf(c->c.disable()).sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a->a
                        .requestMatchers("GET","/api/v1/products/**").permitAll()
                        .requestMatchers("/api/v1/products/**").hasRole("ADMIN").anyRequest().authenticated())
                .exceptionHandling(e->e
                        .authenticationEntryPoint((req,res,ex)->write(res,401,"UNAUTHORIZED","Missing or invalid authentication token"))
                        .accessDeniedHandler((req,res,ex)->write(res,403,"FORBIDDEN","Admin role required")))
                .addFilterBefore(jwt,UsernamePasswordAuthenticationFilter.class).build();
    }
    private static void write(HttpServletResponse res,int status,String code,String message)throws java.io.IOException{
        res.setStatus(status);res.setContentType("application/json");
        res.getWriter().write("{\"code\":\""+code+"\",\"message\":\""+message+"\"}");
    }
}
