package com.supportiq.config;

import com.supportiq.model.Role;
import com.supportiq.model.User;
import com.supportiq.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Data initializer executed once on application startup.
 * Seeds a default administrator account if no admin user is currently present in the database.
 */
@Slf4j
@Component
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;
    private final String adminName;

    public DataInitializer(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.default-admin.email:admin@supportiq.com}") String adminEmail,
            @Value("${app.default-admin.password:Admin@12345}") String adminPassword,
            @Value("${app.default-admin.name:SupportIQ Admin}") String adminName) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail.trim().toLowerCase();
        this.adminPassword = adminPassword;
        this.adminName = adminName;
    }

    @Override
    @Transactional
    public void run(String... args) {
        boolean adminExists = userRepository.existsByRole(Role.ADMIN);

        if (!adminExists && !userRepository.existsByEmail(adminEmail)) {
            User admin = User.builder()
                    .name(adminName)
                    .email(adminEmail)
                    .password(passwordEncoder.encode(adminPassword)) // Password hashed with BCrypt
                    .role(Role.ADMIN)
                    .build();

            userRepository.save(admin);
            log.info("Default administrator created successfully with email: {}", adminEmail);
        } else {
            log.info("Administrator account already exists. Skipping default admin initialization.");
        }
    }
}
