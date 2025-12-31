package org.william.cex.api.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.william.cex.api.dto.request.CreateOrderRequest;
import org.william.cex.api.dto.response.OrderResponse;
import org.william.cex.domain.order.entity.Order;
import org.william.cex.domain.order.service.OrderService;

@RestController
@RequestMapping("/v1/orders")
@Slf4j
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @Valid @RequestBody CreateOrderRequest request) {

        try {
            Long userId = extractUserIdFromAuth(authHeader);
            Order.OrderType orderType = Order.OrderType.valueOf(request.getOrderType().toUpperCase());

            Order order = orderService.createOrder(
                    userId,
                    orderType,
                    request.getBaseCurrency(),
                    request.getQuoteCurrency(),
                    request.getAmount(),
                    request.getPrice()
            );

            OrderResponse response = mapToResponse(order);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error creating order", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable Long orderId) {

        try {
            Long userId = extractUserIdFromAuth(authHeader);
            Order order = orderService.getOrder(orderId);

            // Verify ownership
            if (!order.getUserId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            OrderResponse response = mapToResponse(order);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting order", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> cancelOrder(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @PathVariable Long orderId) {

        try {
            Long userId = extractUserIdFromAuth(authHeader);
            Order order = orderService.getOrder(orderId);

            // Verify ownership
            if (!order.getUserId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            orderService.cancelOrder(orderId);
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Error cancelling order", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    private OrderResponse mapToResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .orderType(order.getOrderType().toString())
                .baseCurrency(order.getBaseCurrency())
                .quoteCurrency(order.getQuoteCurrency())
                .amount(order.getAmount())
                .price(order.getPrice())
                .filledAmount(order.getFilledAmount())
                .status(order.getStatus().toString())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private Long extractUserIdFromAuth(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Invalid or missing Authorization header");
        }
        try {
            return Long.parseLong(authHeader.substring(7));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid user ID in Authorization header");
        }
    }
}

