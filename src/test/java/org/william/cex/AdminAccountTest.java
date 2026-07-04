package org.william.cex;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.william.cex.dto.request.AdminRegisterRequest;
import org.william.cex.dto.request.LoginRequest;
import org.william.cex.dto.response.AuthResponse;
import org.william.cex.repository.UserRepository;
import org.william.cex.support.IntegrationTestBase;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive test suite for Admin Account functionality
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminAccountTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Value("${admin.registration.key}")
    private String adminKey;

    private String adminToken;
    private static String testAdminEmail = "test-admin@example.com";
    private static String testAdminPassword = "AdminPass123!";

    @BeforeAll
    void setUp() throws Exception {
        log.info("Setting up test with admin key: {}", adminKey);
    }

    @Test
    @Order(1)
    @DisplayName("Test 1: Admin Registration - Success")
    void testAdminRegistrationSuccess() throws Exception {
        log.info("=== TEST 1: Admin Registration Success ===");

        AdminRegisterRequest request = AdminRegisterRequest.builder()
                .email(testAdminEmail)
                .password(testAdminPassword)
                .adminKey(adminKey)
                .build();

        MvcResult result = mockMvc.perform(post("/v1/admin/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.email").value(testAdminEmail))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();

        String responseBody = result.getResponse()
                .getContentAsString();
        log.info("Admin registration response: {}", responseBody);

        // Extract token for subsequent tests
        adminToken = objectMapper.readTree(responseBody)
                .get("token")
                .asText();
        log.info("Admin token extracted: {}", adminToken.substring(0, 20) + "...");
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Admin Registration - Invalid Admin Key")
    void testAdminRegistrationInvalidKey() throws Exception {
        log.info("=== TEST 2: Admin Registration with Invalid Key ===");

        AdminRegisterRequest request = AdminRegisterRequest.builder()
                .email("invalid-admin@example.com")
                .password("Password123!")
                .adminKey("wrong-admin-key")
                .build();

        mockMvc.perform(post("/v1/admin/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        log.info("Invalid admin key correctly rejected");
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: Admin Registration - Duplicate Email")
    void testAdminRegistrationDuplicateEmail() throws Exception {
        log.info("=== TEST 3: Admin Registration with Duplicate Email ===");

        AdminRegisterRequest request = AdminRegisterRequest.builder()
                .email(testAdminEmail) // Same email as Test 1
                .password("AnotherPassword123!")
                .adminKey(adminKey)
                .build();

        mockMvc.perform(post("/v1/admin/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        log.info("Duplicate email correctly rejected");
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: Admin Login - Success")
    void testAdminLoginSuccess() throws Exception {
        log.info("=== TEST 4: Admin Login Success ===");

        LoginRequest request = LoginRequest.builder()
                .email(testAdminEmail)
                .password(testAdminPassword)
                .build();

        MvcResult result = mockMvc.perform(post("/v1/admin/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.email").value(testAdminEmail))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();

        String responseBody = result.getResponse()
                .getContentAsString();
        AuthResponse authResponse = objectMapper.readValue(responseBody, AuthResponse.class);
        adminToken = authResponse.getToken();
        log.info("Admin login response: {}", responseBody);

        // Update token
        adminToken = objectMapper.readTree(responseBody)
                .get("token")
                .asText();
        log.info("Admin token refreshed successfully");
    }

    @Test
    @Order(5)
    @DisplayName("Test 5: Admin Login - Invalid Password")
    void testAdminLoginInvalidPassword() throws Exception {
        log.info("=== TEST 5: Admin Login with Invalid Password ===");

        LoginRequest request = LoginRequest.builder()
                .email(testAdminEmail)
                .password("WrongPassword123!")
                .build();

        mockMvc.perform(post("/v1/admin/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        log.info("Invalid password correctly rejected");
    }

    @Test
    @Order(6)
    @DisplayName("Test 6: Admin Login - Non-existent Email")
    void testAdminLoginNonExistentEmail() throws Exception {
        log.info("=== TEST 6: Admin Login with Non-existent Email ===");

        LoginRequest request = LoginRequest.builder()
                .email("nonexistent@example.com")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/v1/admin/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        log.info("Non-existent email correctly rejected");
    }



    @Test
    @Order(10)
    @DisplayName("Test 13: Admin Registration - Short Password")
    void testAdminRegistrationShortPassword() throws Exception {
        log.info("=== TEST 13: Admin Registration with Short Password ===");

        AdminRegisterRequest request = AdminRegisterRequest.builder()
                .email("short-pass@example.com")
                .password("Pass1!")
                .adminKey(adminKey)
                .build();

        mockMvc.perform(post("/v1/admin/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        log.info("Short password correctly rejected");
    }

    @AfterAll
    void tearDown() {
        log.info("=== Cleaning up test data ===");
        try {
            // Clean up test admin
            userRepository.findByEmail(testAdminEmail)
                    .ifPresent(user -> {
                        userRepository.delete(user);
                        log.info("Test admin cleaned up: {}", testAdminEmail);
                    });
        } catch (Exception e) {
            log.warn("Error during cleanup: {}", e.getMessage());
        }
    }
}

