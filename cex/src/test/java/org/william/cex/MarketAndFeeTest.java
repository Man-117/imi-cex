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
import org.william.cex.api.dto.request.RegisterUserRequest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive test suite for Market and Fee functions
 * Note: Market endpoints require authentication in this application
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Slf4j
class MarketAndFeeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String userToken;

    @BeforeAll
    static void setup(@Autowired MockMvc mockMvc, @Autowired ObjectMapper objectMapper) throws Exception {
        log.info("=== Setting up test user for Market tests ===");

        // Register a user to get a token for authenticated endpoints
        RegisterUserRequest registerRequest = RegisterUserRequest.builder()
                .email("market-test@example.com")
                .password("MarketTest123!")
                .build();

        MvcResult registerResult = mockMvc.perform(post("/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        String responseBody = registerResult.getResponse().getContentAsString();
        userToken = objectMapper.readTree(responseBody).get("token").asText();
        log.info("Test user created and token obtained");
    }

    @Test
    @Order(1)
    @DisplayName("Test 1: Get Market Price - BTC/USD (Authenticated)")
    void testGetMarketPriceBTCUSD() throws Exception {
        log.info("=== TEST 1: Get Market Price BTC/USD ===");

        // The endpoint is /v1/market/price/{base}/{quote}
        MvcResult result = mockMvc.perform(get("/v1/market/price/BTC/USD")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pair").exists())
                .andExpect(jsonPath("$.price").exists())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        log.info("Market price BTC/USD response: {}", responseBody);
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Get Market Price - ETH/USD (Authenticated)")
    void testGetMarketPriceETHUSD() throws Exception {
        log.info("=== TEST 2: Get Market Price ETH/USD ===");

        MvcResult result = mockMvc.perform(get("/v1/market/price/ETH/USD")
                .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pair").exists())
                .andExpect(jsonPath("$.price").exists())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        log.info("Market price ETH/USD response: {}", responseBody);
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: Get Market Price - Without Authentication")
    void testGetMarketPriceWithoutAuth() throws Exception {
        log.info("=== TEST 3: Get Market Price without Authentication ===");

        // Market endpoints require authentication
        mockMvc.perform(get("/v1/market/price/BTC/USD"))
                .andExpect(status().isUnauthorized());

        log.info("Unauthorized access correctly rejected");
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: Get All Fee Rates (Public)")
    void testGetAllFeeRates() throws Exception {
        log.info("=== TEST 4: Get All Fee Rates (Admin Endpoint - Public) ===");

        // The /v1/admin/fees endpoint is configured as public in SecurityConfig
        MvcResult result = mockMvc.perform(get("/v1/admin/fees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        log.info("All fee rates response: {}", responseBody);
    }

    @AfterAll
    static void tearDown(@Autowired org.william.cex.domain.user.repository.UserRepository userRepository) {
        log.info("=== Cleaning up test data ===");
        try {
            userRepository.findByEmail("market-test@example.com").ifPresent(user -> {
                userRepository.delete(user);
                log.info("Test user cleaned up: market-test@example.com");
            });
        } catch (Exception e) {
            log.warn("Error during cleanup: {}", e.getMessage());
        }
    }
}

