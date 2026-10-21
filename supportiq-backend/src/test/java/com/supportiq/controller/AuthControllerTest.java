package com.supportiq.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.supportiq.dto.LoginRequest;
import com.supportiq.dto.RegisterRequest;
import com.supportiq.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanUpDatabase() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("1. POST /api/auth/register should succeed and return 201 with UserResponse")
    void registerSuccess() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .name("Alice Johnson")
                .email("alice@example.com")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.name").value("Alice Johnson"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("2. POST /api/auth/register with duplicate email should return 409 Conflict")
    void duplicateEmailGives409() throws Exception {
        RegisterRequest initialRequest = RegisterRequest.builder()
                .name("Bob Smith")
                .email("bob@example.com")
                .password("password123")
                .build();

        // Initial successful registration
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(initialRequest)))
                .andExpect(status().isCreated());

        // Duplicate registration attempt with identical email
        RegisterRequest duplicateRequest = RegisterRequest.builder()
                .name("Bob Duplicate")
                .email("bob@example.com")
                .password("differentPass123")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(duplicateRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message", containsString("already registered")));
    }

    @Test
    @DisplayName("3. POST /api/auth/login should authenticate and return 200 with JWT token")
    void loginGivesToken() throws Exception {
        // First register a test user
        RegisterRequest registerRequest = RegisterRequest.builder()
                .name("Charlie Brown")
                .email("charlie@example.com")
                .password("Password@123")
                .build();

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated());

        // Now attempt login with registered credentials
        LoginRequest loginRequest = LoginRequest.builder()
                .email("charlie@example.com")
                .password("Password@123")
                .build();

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.email").value("charlie@example.com"))
                .andExpect(jsonPath("$.user.name").value("Charlie Brown"))
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"));
    }
}
