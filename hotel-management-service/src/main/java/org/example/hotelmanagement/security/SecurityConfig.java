package org.example.hotelmanagement.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.common.filter.TraceIdFilter;
import org.example.common.security.JwtAuthenticationFilter;
import org.example.hotelmanagement.filter.InternalServiceAuthFilter;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public InternalServiceAuthFilter internalServiceAuthFilter() {
        return new InternalServiceAuthFilter();
    }

    @Bean
    public AccessDeniedHandler jsonAccessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            String traceId = MDC.get("traceId");
            Map<String, Object> body = new HashMap<>();
            body.put("error", "Forbidden");
            body.put("message", accessDeniedException.getMessage() != null ? accessDeniedException.getMessage() : "Access denied");
            body.put("status", 403);
            body.put("timestamp", LocalDateTime.now().toString());
            body.put("path", request.getRequestURI());
            body.put("traceId", traceId != null ? traceId : "unknown");
            new ObjectMapper().writeValue(response.getOutputStream(), body);
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                    TraceIdFilter traceIdFilter,
                                                    InternalServiceAuthFilter internalServiceAuthFilter,
                                                    JwtAuthenticationFilter jwtFilter,
                                                    AccessDeniedHandler jsonAccessDeniedHandler) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**", "/h2-console/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/api/hotels").permitAll()
                        .requestMatchers("/api/rooms").permitAll()
                        .requestMatchers("/api/rooms/recommend").permitAll()
                        .requestMatchers("/api/rooms/{id}").permitAll()
                        .requestMatchers("/api/hotels/**").authenticated()
                        .requestMatchers("/api/rooms/**").authenticated()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex.accessDeniedHandler(jsonAccessDeniedHandler))
                .headers(headers -> headers.frameOptions(frame -> frame.disable()))
                .addFilterBefore(traceIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(internalServiceAuthFilter, JwtAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
