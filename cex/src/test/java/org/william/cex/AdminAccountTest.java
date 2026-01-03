package org.william.cex;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.william.cex.api.dto.request.AdminRegisterRequest;
import org.william.cex.api.dto.request.LoginRequest;
import org.william.cex.api.dto.request.UpdateFeeRateRequest;
import org.william.cex.domain.user.repository.UserRepository;
import org.william.cex.domain.admin.repository.AdministratorRepository;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.hamcrest.Matchers.*;

/**
 * Comprehensive test suite for Admin Account functionality
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Slf4j
class AdminAccountTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AdministratorRepository administratorRepository;

    @Value("${admin.registration.key}")
    private String adminKey;

    private static String adminToken;
    private static String testAdminEmail = "test-admin@example.com";
    private static String testAdminPassword = "AdminPass123!";

    @BeforeEach
    void setUp() {
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

        MvcResult result = mockMvc.perform(post("/v1/admin/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.email").value(testAdminEmail))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        log.info("Admin registration response: {}", responseBody);

        // Extract token for subsequent tests
        adminToken = objectMapper.readTree(responseBody).get("token").asText();
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

        mockMvc.perform(post("/v1/admin/register")
                .contentType(MediaType.APPLICATION_JSON)
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

        mockMvc.perform(post("/v1/admin/register")
                .contentType(MediaType.APPLICATION_JSON)
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

        MvcResult result = mockMvc.perform(post("/v1/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.userId").exists())
                .andExpect(jsonPath("$.email").value(testAdminEmail))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        log.info("Admin login response: {}", responseBody);

        // Update token
        adminToken = objectMapper.readTree(responseBody).get("token").asText();
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

        mockMvc.perform(post("/v1/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
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

        mockMvc.perform(post("/v1/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        log.info("Non-existent email correctly rejected");
    }

    @Test
    @Order(7)
    @DisplayName("Test 7: Get Account Balance - With Authentication")
    void testGetAccountBalanceWithAuth() throws Exception {
        log.info("=== TEST 7: Get Account Balance with Authentication ===");

        MvcResult result = mockMvc.perform(get("/v1/admin/account/balance")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDeposits").exists())
                .andExpect(jsonPath("$.totalWithdrawals").exists())
                .andExpect(jsonPath("$.totalFees").exists())
                .andExpect(jsonPath("$.firmHoldings").exists())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        log.info("Account balance response: {}", responseBody);
    }

    @Test
    @Order(8)
    @DisplayName("Test 8: Get Account Balance - Without Authentication")
    void testGetAccountBalanceWithoutAuth() throws Exception {
        log.info("=== TEST 8: Get Account Balance without Authentication ===");

        mockMvc.perform(get("/v1/admin/account/balance"))
                .andExpect(status().isUnauthorized());

        log.info("Unauthorized access correctly rejected");
    }

    @Test
    @Order(9)
    @DisplayName("Test 9: Update Fee Rate - With Authentication")
    void testUpdateFeeRateWithAuth() throws Exception {
        log.info("=== TEST 9: Update Fee Rate with Authentication ===");

        UpdateFeeRateRequest request = UpdateFeeRateRequest.builder()
                .currencyPair("BTC/USD")
                .feePercentage(new BigDecimal("0.25"))
                .build();

        MvcResult result = mockMvc.perform(post("/v1/admin/fees")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.currencyPair").value("BTC/USD"))
                .andExpect(jsonPath("$.feePercentage").value(0.25))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        log.info("Fee rate update response: {}", responseBody);
    }

    @Test
    @Order(10)
    @DisplayName("Test 10: Update Fee Rate - Without Authentication")
    void testUpdateFeeRateWithoutAuth() throws Exception {
        log.info("=== TEST 10: Update Fee Rate without Authentication ===");

        UpdateFeeRateRequest request = UpdateFeeRateRequest.builder()
                .currencyPair("ETH/USD")
                .feePercentage(new BigDecimal("0.30"))
                .build();

        mockMvc.perform(post("/v1/admin/fees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        log.info("Unauthorized fee update correctly rejected");
    }

    @Test
    @Order(11)
    @DisplayName("Test 11: Get All Fee Rates - Public Access")
    void testGetAllFeeRates() throws Exception {
        log.info("=== TEST 11: Get All Fee Rates (Public Access) ===");

        MvcResult result = mockMvc.perform(get("/v1/admin/fees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        log.info("All fee rates response: {}", responseBody);
    }

    @Test
    @Order(12)
    @DisplayName("Test 12: Admin Registration - Invalid Email Format")
    void testAdminRegistrationInvalidEmailFormat() throws Exception {
        log.info("=== TEST 12: Admin Registration with Invalid Email Format ===");

        AdminRegisterRequest request = AdminRegisterRequest.builder()
                .email("invalid-email-format")
                .password("Password123!")
                .adminKey(adminKey)
                .build();

        mockMvc.perform(post("/v1/admin/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        log.info("Invalid email format correctly rejected");
    }

    @Test
    @Order(13)
    @DisplayName("Test 13: Admin Registration - Short Password")
    void testAdminRegistrationShortPassword() throws Exception {
        log.info("=== TEST 13: Admin Registration with Short Password ===");

        AdminRegisterRequest request = AdminRegisterRequest.builder()
                .email("short-pass@example.com")
                .password("Pass1!")
                .adminKey(adminKey)
                .build();

        mockMvc.perform(post("/v1/admin/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        log.info("Short password correctly rejected");
    }

    @AfterAll
    static void tearDown(@Autowired UserRepository userRepository,
                         @Autowired AdministratorRepository administratorRepository) {
        log.info("=== Cleaning up test data ===");
        try {
            // Clean up test admin
            userRepository.findByEmail(testAdminEmail).ifPresent(user -> {
                administratorRepository.findByUserId(user.getId()).ifPresent(administratorRepository::delete);
                userRepository.delete(user);
                log.info("Test admin cleaned up: {}", testAdminEmail);
            });
        } catch (Exception e) {
            log.warn("Error during cleanup: {}", e.getMessage());
        }
    }
}

