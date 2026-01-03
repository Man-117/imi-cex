package org.william.cex;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.william.cex.api.dto.request.LoginRequest;
import org.william.cex.api.dto.request.RegisterUserRequest;
import org.william.cex.domain.user.repository.UserRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive test suite for Authentication functions
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Slf4j
class AuthenticationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    private static String testUserEmail = "testuser@example.com";
    private static String testUserPassword = "TestPass123!";
    private static String userToken;

    @Test
    @Order(1)
    @DisplayName("Test 1: User Registration - Success")
    void testUserRegistrationSuccess() throws Exception {
        log.info("=== TEST 1: User Registration Success ===");

        RegisterUserRequest request = RegisterUserRequest.builder()
                .email(testUserEmail)
                .password(testUserPassword)
                .build();

        MvcResult result = mockMvc.perform(post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.email").value(testUserEmail))
                .andExpect(jsonPath("$.role").value("USER"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        log.info("User registration response: {}", responseBody);

        userToken = objectMapper.readTree(responseBody).get("token").asText();
        log.info("User token extracted: {}", userToken.substring(0, 20) + "...");
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: User Registration - Duplicate Email")
    void testUserRegistrationDuplicateEmail() throws Exception {
        log.info("=== TEST 2: User Registration with Duplicate Email ===");

        RegisterUserRequest request = RegisterUserRequest.builder()
                .email(testUserEmail)
                .password("AnotherPassword123!")
                .build();

        mockMvc.perform(post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        log.info("Duplicate email correctly rejected");
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: User Registration - Invalid Email Format")
    void testUserRegistrationInvalidEmail() throws Exception {
        log.info("=== TEST 3: User Registration with Invalid Email ===");

        RegisterUserRequest request = RegisterUserRequest.builder()
                .email("invalid-email")
                .password("TestPass123!")
                .build();

        mockMvc.perform(post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        log.info("Invalid email format correctly rejected");
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: User Registration - Short Password")
    void testUserRegistrationShortPassword() throws Exception {
        log.info("=== TEST 4: User Registration with Short Password ===");

        RegisterUserRequest request = RegisterUserRequest.builder()
                .email("shortpass@example.com")
                .password("Pass1!")
                .build();

        mockMvc.perform(post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        log.info("Short password correctly rejected");
    }

    @Test
    @Order(5)
    @DisplayName("Test 5: User Login - Success")
    void testUserLoginSuccess() throws Exception {
        log.info("=== TEST 5: User Login Success ===");

        LoginRequest request = LoginRequest.builder()
                .email(testUserEmail)
                .password(testUserPassword)
                .build();

        MvcResult result = mockMvc.perform(post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.email").value(testUserEmail))
                .andExpect(jsonPath("$.role").value("USER"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        log.info("User login response: {}", responseBody);

        userToken = objectMapper.readTree(responseBody).get("token").asText();
        log.info("User token refreshed successfully");
    }

    @Test
    @Order(6)
    @DisplayName("Test 6: User Login - Invalid Password")
    void testUserLoginInvalidPassword() throws Exception {
        log.info("=== TEST 6: User Login with Invalid Password ===");

        LoginRequest request = LoginRequest.builder()
                .email(testUserEmail)
                .password("WrongPassword123!")
                .build();

        mockMvc.perform(post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        log.info("Invalid password correctly rejected");
    }

    @Test
    @Order(7)
    @DisplayName("Test 7: User Login - Non-existent Email")
    void testUserLoginNonExistentEmail() throws Exception {
        log.info("=== TEST 7: User Login with Non-existent Email ===");

        LoginRequest request = LoginRequest.builder()
                .email("nonexistent@example.com")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        log.info("Non-existent email correctly rejected");
    }

    @AfterAll
    static void tearDown(@Autowired UserRepository userRepository) {
        log.info("=== Cleaning up test data ===");
        try {
            userRepository.findByEmail(testUserEmail).ifPresent(userRepository::delete);
            log.info("Test user cleaned up: {}", testUserEmail);
        } catch (Exception e) {
            log.warn("Error during cleanup: {}", e.getMessage());
        }
    }
}

