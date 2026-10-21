package com.supportiq.config;

import com.supportiq.security.CustomAccessDeniedHandler;
import com.supportiq.security.JwtAuthenticationEntryPoint;
import com.supportiq.security.JwtAuthenticationFilter;
import com.supportiq.service.CustomUserDetailsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Spring Security 6.x configuration.
 *
 * WHY STATELess SESSIONS:
 * In a scalable REST API, maintaining server-side HTTP sessions creates synchronization
 * overhead and prevents simple horizontal scaling behind a load balancer. We configure
 * SessionCreationPolicy.STATELESS so Spring Security does not create or rely on HttpSession.
 *
 * WHY CSRF IS DISABLED:
 * Cross-Site Request Forgery attacks rely on browsers automatically attaching session cookies
 * to cross-site requests. Because our API is completely stateless and authenticates requests
 * exclusively through explicit Authorization headers (Bearer JWT tokens) rather than cookies,
 * CSRF attacks are not applicable and disabling CSRF is safe and recommended.
 *
 * WHY BCRYPT PASSWORD ENCODING:
 * BCrypt is a battle-tested, one-way password hashing algorithm incorporating a 128-bit salt
 * and an adaptive cost parameter (work factor). This makes rainbow table and GPU-accelerated
 * brute-force attacks computationally infeasible.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;
    private final CustomUserDetailsService userDetailsService;
    private final CorsConfigurationSource corsConfigurationSource;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Disable CSRF since this is a stateless REST API using Bearer tokens
                .csrf(AbstractHttpConfigurer::disable)
                // Attach CORS configuration to support the Vite React frontend
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                // Ensure stateless session management (no HTTP session is created or used)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Handle unauthenticated (401) and unauthorized (403) access with clean JSON responses
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        .accessDeniedHandler(customAccessDeniedHandler)
                )
                // Define URL-level authorization rules
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints for registration and authentication
                        .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                        // Admin-only endpoints require the ADMIN role (checks for "ROLE_ADMIN" authority)
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // All other endpoints require a valid authenticated JWT
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                // Place the JWT authentication filter before the standard UsernamePasswordAuthenticationFilter
                // WHY: We must intercept the Bearer token and populate the SecurityContext BEFORE Spring Security
                // evaluates endpoint authorization rules.
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // Strength factor 10 is the secure standard default for BCrypt
        return new BCryptPasswordEncoder();
    }
}
