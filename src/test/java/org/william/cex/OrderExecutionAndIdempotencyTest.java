package org.william.cex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.william.cex.dto.request.AddBalanceRequest;
import org.william.cex.dto.request.CreateOrderRequest;
import org.william.cex.dto.request.RegisterUserRequest;
import org.william.cex.support.IntegrationTestBase;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderExecutionAndIdempotencyTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Order matching executes trade and settles wallets")
    void testOrderMatchingAndSettlement() throws Exception {
        String buyerToken = registerUser("buyer+" + UUID.randomUUID() + "@example.com");
        String sellerToken = registerUser("seller+" + UUID.randomUUID() + "@example.com");

        addBalance(buyerToken, "USD", new BigDecimal("100000"));
        addBalance(sellerToken, "BTC", new BigDecimal("1"));

        Long buyOrderId = createOrder(
                buyerToken,
                "BUY",
                "BTC",
                "USD",
                new BigDecimal("1"),
                new BigDecimal("50000"),
                null
        ).path("id").asLong();

        Long sellOrderId = createOrder(
                sellerToken,
                "SELL",
                "BTC",
                "USD",
                new BigDecimal("1"),
                new BigDecimal("49000"),
                null
        ).path("id").asLong();

        JsonNode buyOrder = getOrder(buyerToken, buyOrderId);
        JsonNode sellOrder = getOrder(sellerToken, sellOrderId);
        assertEquals("FILLED", buyOrder.path("status").asText());
        assertEquals("FILLED", sellOrder.path("status").asText());

        JsonNode buyerBtcBalance = getBalance(buyerToken, "BTC");
        JsonNode sellerUsdBalance = getBalance(sellerToken, "USD");
        assertEquals(0, new BigDecimal("1.00000000").compareTo(new BigDecimal(buyerBtcBalance.path("balance").asText())));
        assertTrue(new BigDecimal(sellerUsdBalance.path("balance").asText()).compareTo(new BigDecimal("48900")) > 0);
    }

    @Test
    @DisplayName("Idempotency key returns original write response")
    void testIdempotencyForBalanceAndOrderCreate() throws Exception {
        String token = registerUser("idem+" + UUID.randomUUID() + "@example.com");

        String balanceKey = "idem-balance-" + UUID.randomUUID();
        JsonNode firstBalance = addBalance(token, "USD", new BigDecimal("5000"), balanceKey);
        JsonNode secondBalance = addBalance(token, "USD", new BigDecimal("5000"), balanceKey);
        assertEquals(
                0,
                new BigDecimal(firstBalance.path("balance").asText())
                        .compareTo(new BigDecimal(secondBalance.path("balance").asText()))
        );

        String orderKey = "idem-order-" + UUID.randomUUID();
        JsonNode firstOrder = createOrder(
                token, "BUY", "BTC", "USD",
                new BigDecimal("0.1"), new BigDecimal("30000"), orderKey
        );
        JsonNode secondOrder = createOrder(
                token, "BUY", "BTC", "USD",
                new BigDecimal("0.1"), new BigDecimal("30000"), orderKey
        );
        assertEquals(firstOrder.path("id").asLong(), secondOrder.path("id").asLong());
    }

    private String registerUser(String email) throws Exception {
        RegisterUserRequest request = RegisterUserRequest.builder()
                .email(email)
                .password("TestPass123!")
                .build();

        MvcResult result = mockMvc.perform(post("/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
    }

    private JsonNode addBalance(String token, String currency, BigDecimal amount) throws Exception {
        return addBalance(token, currency, amount, null);
    }

    private JsonNode addBalance(String token, String currency, BigDecimal amount, String idempotencyKey) throws Exception {
        AddBalanceRequest request = AddBalanceRequest.builder()
                .currency(currency)
                .amount(amount)
                .build();

        var builder = post("/v1/balance/add")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request));

        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }

        MvcResult result = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode createOrder(String token,
                                 String orderType,
                                 String baseCurrency,
                                 String quoteCurrency,
                                 BigDecimal amount,
                                 BigDecimal price,
                                 String idempotencyKey) throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .orderType(orderType)
                .baseCurrency(baseCurrency)
                .quoteCurrency(quoteCurrency)
                .amount(amount)
                .price(price)
                .build();

        var builder = post("/v1/orders")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request));

        if (idempotencyKey != null) {
            builder.header("Idempotency-Key", idempotencyKey);
        }

        MvcResult result = mockMvc.perform(builder)
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getOrder(String token, Long orderId) throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/orders/" + orderId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private JsonNode getBalance(String token, String currency) throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/balance/" + currency)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
