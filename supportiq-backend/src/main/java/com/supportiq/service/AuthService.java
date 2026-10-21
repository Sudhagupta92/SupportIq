package com.supportiq.service;

import com.supportiq.dto.AuthResponse;
import com.supportiq.dto.LoginRequest;
import com.supportiq.dto.RegisterRequest;
import com.supportiq.dto.UserResponse;
import com.supportiq.exception.DuplicateEmailException;
import com.supportiq.exception.InvalidCredentialsException;
import com.supportiq.exception.ResourceNotFoundException;
import com.supportiq.model.Role;
import com.supportiq.model.User;
import com.supportiq.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    /**
     * Registers a new customer in the system.
     *
     * WHY PASSWORD HASHING (BCrypt):
     * Storing raw passwords is a catastrophic security risk. BCrypt uses a salted,
     * adaptive hashing algorithm with an exponential cost factor. The embedded salt prevents
     * rainbow table lookups, and the computational complexity defends against brute-force attacks.
     *
     * WHY ROLE CUSTOMER:
     * To prevent privilege escalation attacks, user registration NEVER accepts a role from the client.
     * All self-registered users are strictly assigned Role.CUSTOMER.
     */
    @Transactional
    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new DuplicateEmailException("Email is already registered: " + request.getEmail());
        }

        User user = User.builder()
                .name(request.getName().trim())
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.CUSTOMER) // Strictly hardcoded to CUSTOMER
                .build();

        User savedUser = userRepository.save(user);
        log.info("New customer registered with ID: {} and email: {}", savedUser.getId(), savedUser.getEmail());

        return UserResponse.fromEntity(savedUser);
    }

    /**
     * Authenticates a user and generates a stateless JWT.
     */
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(normalizedEmail, request.getPassword())
            );
        } catch (BadCredentialsException e) {
            log.warn("Failed login attempt for email: {}", normalizedEmail);
            throw new InvalidCredentialsException("Invalid email or password.");
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + normalizedEmail));

        String token = jwtService.generateToken(user);

        return AuthResponse.builder()
                .token(token)
                .tokenType("Bearer")
                .user(UserResponse.fromEntity(user))
                .build();
    }

    /**
     * Retrieves profile information for the authenticated user.
     */
    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        String normalizedEmail = email.trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + email));

        return UserResponse.fromEntity(user);
    }
}
