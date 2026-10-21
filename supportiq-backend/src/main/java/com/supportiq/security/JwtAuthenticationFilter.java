package com.supportiq.security;

import com.supportiq.service.CustomUserDetailsService;
import com.supportiq.service.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter that intercepts incoming HTTP requests to validate JWT tokens.
 *
 * WHY OncePerRequestFilter:
 * Extends OncePerRequestFilter to guarantee execution exactly once per request dispatch,
 * preventing duplicate filtering during internal forward or error dispatches.
 *
 * WHY STATELess AUTHENTICATION:
 * Since REST APIs should be stateless, we do not store sessions in server memory or Redis.
 * Every request carries its own cryptographic proof of identity in the "Authorization: Bearer <token>"
 * header. We parse the token, verify its HMAC signature, and set the SecurityContextHolder so
 * downstream filters and controllers know the user's identity and authorities.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // If no Authorization header or not Bearer format, continue filter chain without setting authentication
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7);

        try {
            final String userEmail = jwtService.extractEmail(jwt);

            // Only authenticate if email exists and no authentication has been set in this thread's SecurityContext yet
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(userEmail);

                if (jwtService.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }
        } catch (JwtException e) {
            log.warn("Cannot set user authentication from JWT: {}", e.getMessage());
            // Clear context if token is malformed or expired
            SecurityContextHolder.clearContext();
        } catch (Exception e) {
            log.error("Unexpected error in JWT authentication filter: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
