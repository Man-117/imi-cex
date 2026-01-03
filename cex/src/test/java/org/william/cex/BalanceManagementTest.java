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
import org.william.cex.api.dto.request.AddBalanceRequest;
import org.william.cex.api.dto.request.LoginRequest;
import org.william.cex.api.dto.request.RegisterUserRequest;
import org.william.cex.domain.user.repository.UserRepository;
import org.william.cex.domain.user.repository.UserWalletRepository;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive test suite for Balance Management functions
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Slf4j
class BalanceManagementTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserWalletRepository userWalletRepository;

    private static String testUserEmail = "balance-test@example.com";
    private static String testUserPassword = "BalanceTest123!";
    private static String userToken;

    @BeforeAll
    static void setup(@Autowired MockMvc mockMvc, @Autowired ObjectMapper objectMapper) throws Exception {
        log.info("=== Setting up test user for Balance Management tests ===");

        RegisterUserRequest request = RegisterUserRequest.builder()
                .email(testUserEmail)
                .password(testUserPassword)
                .build();

        MvcResult result = mockMvc.perform(post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        userToken = objectMapper.readTree(responseBody).get("token").asText();
        log.info("Test user created and token obtained");
    }

    @Test
    @Order(1)
    @DisplayName("Test 1: Add Balance - Success (BTC)")
    void testAddBalanceBTCSuccess() throws Exception {
        log.info("=== TEST 1: Add Balance BTC Success ===");

        AddBalanceRequest request = AddBalanceRequest.builder()
                .currency("BTC")
                .amount(new BigDecimal("1.5"))
                .build();

        MvcResult result = mockMvc.perform(post("/v1/balance/add")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("BTC"))
                .andExpect(jsonPath("$.balance").value(1.5))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        log.info("Add balance BTC response: {}", responseBody);
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Add Balance - Success (USD)")
    void testAddBalanceUSDSuccess() throws Exception {
        log.info("=== TEST 2: Add Balance USD Success ===");

        AddBalanceRequest request = AddBalanceRequest.builder()
                .currency("USD")
                .amount(new BigDecimal("10000"))
                .build();

        MvcResult result = mockMvc.perform(post("/v1/balance/add")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.balance").value(10000))
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        log.info("Add balance USD response: {}", responseBody);
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: Add Balance - Idempotency Check")
    void testAddBalanceIdempotency() throws Exception {
        log.info("=== TEST 3: Add Balance Idempotency ===");

        AddBalanceRequest request = AddBalanceRequest.builder()
                .currency("ETH")
                .amount(new BigDecimal("5.0"))
                .build();

        // First request
        MvcResult result1 = mockMvc.perform(post("/v1/balance/add")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("ETH"))
                .andExpect(jsonPath("$.balance").value(5.0))
                .andReturn();

        log.info("First add balance ETH: {}", result1.getResponse().getContentAsString());

        // Second request (should add more)
        MvcResult result2 = mockMvc.perform(post("/v1/balance/add")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("ETH"))
                .andExpect(jsonPath("$.balance").value(10.0))
                .andReturn();

        log.info("Second add balance ETH: {}", result2.getResponse().getContentAsString());
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: Add Balance - Negative Amount")
    void testAddBalanceNegativeAmount() throws Exception {
        log.info("=== TEST 4: Add Balance with Negative Amount ===");

        AddBalanceRequest request = AddBalanceRequest.builder()
                .currency("BTC")
                .amount(new BigDecimal("-1.0"))
                .build();

        mockMvc.perform(post("/v1/balance/add")
                .header("Authorization", "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());

        log.info("Negative amount correctly rejected");
    }

    @Test
    @Order(5)
    @DisplayName("Test 5: Add Balance - Without Authentication")
    void testAddBalanceWithoutAuth() throws Exception {
        log.info("=== TEST 5: Add Balance without Authentication ===");

        AddBalanceRequest request = AddBalanceRequest.builder()
                .currency("BTC")
                .amount(new BigDecimal("1.0"))
                .build();

        mockMvc.perform(post("/v1/balance/add")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        log.info("Unauthorized access correctly rejected");
    }

    @Test
    @Order(6)
    @DisplayName("Test 6: Get Balance - BTC")
    void testGetBalanceBTC() throws Exception {
        log.info("=== TEST 6: Get Balance BTC ===");

        MvcResult result = mockMvc.perform(get("/v1/balance/BTC")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("BTC"))
                .andExpect(jsonPath("$.balance").exists())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        log.info("Get balance BTC response: {}", responseBody);
    }

    @Test
    @Order(7)
    @DisplayName("Test 7: Get Balance - USD")
    void testGetBalanceUSD() throws Exception {
        log.info("=== TEST 7: Get Balance USD ===");

        MvcResult result = mockMvc.perform(get("/v1/balance/USD")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.balance").exists())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        log.info("Get balance USD response: {}", responseBody);
    }

    @Test
    @Order(8)
    @DisplayName("Test 8: Get Balance - Non-existent Currency")
    void testGetBalanceNonExistentCurrency() throws Exception {
        log.info("=== TEST 8: Get Balance Non-existent Currency ===");

        // App returns 404 Not Found for non-existent currency wallet
        mockMvc.perform(get("/v1/balance/DOGE")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());

        log.info("Non-existent currency correctly returns 404");
    }

    @Test
    @Order(9)
    @DisplayName("Test 9: Get Balance - Without Authentication")
    void testGetBalanceWithoutAuth() throws Exception {
        log.info("=== TEST 9: Get Balance without Authentication ===");

        mockMvc.perform(get("/v1/balance/BTC"))
                .andExpect(status().isUnauthorized());

        log.info("Unauthorized access correctly rejected");
    }

    @AfterAll
    static void tearDown(@Autowired UserRepository userRepository,
                         @Autowired UserWalletRepository userWalletRepository) {
        log.info("=== Cleaning up test data ===");
        try {
            userRepository.findByEmail(testUserEmail).ifPresent(user -> {
                userWalletRepository.deleteAll(userWalletRepository.findByUserId(user.getId()));
                userRepository.delete(user);
                log.info("Test user and wallets cleaned up: {}", testUserEmail);
            });
        } catch (Exception e) {
            log.warn("Error during cleanup: {}", e.getMessage());
        }
    }
}

