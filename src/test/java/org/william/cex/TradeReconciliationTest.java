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
import org.william.cex.api.dto.request.AddBalanceRequest;
import org.william.cex.api.dto.request.AdminRegisterRequest;
import org.william.cex.api.dto.request.CreateOrderRequest;
import org.william.cex.api.dto.request.RegisterUserRequest;
import org.william.cex.domain.fee.entity.FeeTransaction;
import org.william.cex.domain.fee.repository.FeeTransactionRepository;
import org.william.cex.domain.order.entity.Order;
import org.william.cex.domain.order.entity.Trade;
import org.william.cex.domain.order.repository.OrderRepository;
import org.william.cex.domain.order.repository.TradeRepository;
import org.william.cex.support.IntegrationTestBase;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TradeReconciliationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private FeeTransactionRepository feeTransactionRepository;

    @Test
    @DisplayName("Trade reconciliation detects breaks and auto-repairs fixable issues")
    void testTradeReconciliationDetectAndRepair() throws Exception {
        String adminToken = registerAdmin("recon-admin+" + UUID.randomUUID() + "@example.com");
        String buyerToken = registerUser("recon-buyer+" + UUID.randomUUID() + "@example.com");
        String sellerToken = registerUser("recon-seller+" + UUID.randomUUID() + "@example.com");

        addBalance(buyerToken, "USD", new BigDecimal("100000"));
        addBalance(sellerToken, "BTC", new BigDecimal("1"));

        Long buyOrderId = createOrder(buyerToken, "BUY", "BTC", "USD", new BigDecimal("1"), new BigDecimal("50000"));
        Long sellOrderId = createOrder(sellerToken, "SELL", "BTC", "USD", new BigDecimal("1"), new BigDecimal("49900"));

        Trade targetTrade = tradeRepository.findByBuyOrderIdOrSellOrderId(buyOrderId, sellOrderId)
                .stream()
                .filter(trade -> trade.getBuyOrderId().equals(buyOrderId) && trade.getSellOrderId().equals(sellOrderId))
                .findFirst()
                .orElseThrow();

        targetTrade.setSettlementStatus("PENDING");
        targetTrade.setSettledAt(null);
        tradeRepository.save(targetTrade);

        Order buyOrder = orderRepository.findById(buyOrderId).orElseThrow();
        buyOrder.setFilledAmount(BigDecimal.ZERO.setScale(8));
        buyOrder.setStatus(Order.OrderStatus.PENDING);
        orderRepository.save(buyOrder);

        List<FeeTransaction> buyFees = feeTransactionRepository.findByOrderId(buyOrderId);
        assertFalse(buyFees.isEmpty());
        feeTransactionRepository.delete(buyFees.get(0));

        MvcResult runResult = mockMvc.perform(post("/v1/admin/reconciliation/trades/run")
                        .param("lookbackHours", "24")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode runResponse = objectMapper.readTree(runResult.getResponse().getContentAsString());
        Long runId = runResponse.path("id").asLong();
        assertTrue(runResponse.path("totalIssues").asInt() >= 3);
        assertTrue(runResponse.toString().contains("UNSETTLED_TRADE"));
        assertTrue(runResponse.toString().contains("ORDER_FILLED_MISMATCH"));
        assertTrue(runResponse.toString().contains("FEE_LEDGER_MISMATCH"));

        MvcResult repairResult = mockMvc.perform(post("/v1/admin/reconciliation/trades/runs/" + runId + "/repair")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode repairResponse = objectMapper.readTree(repairResult.getResponse().getContentAsString());
        assertEquals("REPAIRED", repairResponse.path("status").asText());
        assertTrue(repairResponse.path("autoRepairedIssues").asInt() >= 3);

        Trade repairedTrade = tradeRepository.findById(targetTrade.getId()).orElseThrow();
        Order repairedBuyOrder = orderRepository.findById(buyOrderId).orElseThrow();
        List<FeeTransaction> repairedFees = feeTransactionRepository.findByOrderId(buyOrderId);

        assertEquals("SETTLED", repairedTrade.getSettlementStatus());
        assertTrue(repairedTrade.getSettledAt() != null);
        assertEquals(0, repairedBuyOrder.getFilledAmount().compareTo(new BigDecimal("1.00000000")));
        assertTrue(repairedFees.stream().map(FeeTransaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .compareTo(BigDecimal.ZERO) > 0);
    }

    @Test
    @DisplayName("Trade reconciliation endpoint requires admin credentials")
    void testTradeReconciliationRequiresAuth() throws Exception {
        mockMvc.perform(post("/v1/admin/reconciliation/trades/run")
                        .param("lookbackHours", "24"))
                .andExpect(status().isUnauthorized());
    }

    private String registerAdmin(String email) throws Exception {
        AdminRegisterRequest request = AdminRegisterRequest.builder()
                .email(email)
                .password("AdminPass123!")
                .adminKey("test-admin-registration-key")
                .build();

        MvcResult result = mockMvc.perform(post("/v1/admin/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("token").asText();
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

    private void addBalance(String token, String currency, BigDecimal amount) throws Exception {
        AddBalanceRequest request = AddBalanceRequest.builder()
                .currency(currency)
                .amount(amount)
                .build();

        mockMvc.perform(post("/v1/balance/add")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    private Long createOrder(String token,
                             String orderType,
                             String baseCurrency,
                             String quoteCurrency,
                             BigDecimal amount,
                             BigDecimal price) throws Exception {
        CreateOrderRequest request = CreateOrderRequest.builder()
                .orderType(orderType)
                .baseCurrency(baseCurrency)
                .quoteCurrency(quoteCurrency)
                .amount(amount)
                .price(price)
                .build();

        MvcResult result = mockMvc.perform(post("/v1/orders")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }
}
