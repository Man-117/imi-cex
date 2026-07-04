package org.william.cex.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.william.cex.dto.request.CreateOrderRequest;
import org.william.cex.dto.response.OrderResponse;
import org.william.cex.entity.Order;
import org.william.cex.service.OrderService;
import org.william.cex.service.UserService;
import org.william.cex.infrastructure.security.AuthenticationUtils;

@RestController
@RequestMapping("/v1/orders")
@Slf4j
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private UserService userService;

    @Autowired
    private AuthenticationUtils authenticationUtils;

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {
         Long userId = authenticationUtils.getAuthenticatedUserId();


        Order.OrderType orderType = Order.OrderType.valueOf(request.getOrderType().toUpperCase());

        log.info("User {} is creating {} order: {} {} -> {} at price {}",
                userId, orderType, request.getAmount(), request.getBaseCurrency(),
                request.getQuoteCurrency(), request.getPrice());

        Order order = orderService.createOrder(
                userId,
                orderType,
                request.getBaseCurrency(),
                request.getQuoteCurrency(),
                request.getAmount(),
                request.getPrice()
        );

        OrderResponse response = mapToResponse(order);

        log.info("Order created successfully for user {}: Order ID {} - {} {} at {}",
                userId, order.getId(), request.getAmount(), request.getBaseCurrency(), request.getPrice());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable Long orderId) {
        Long userId = authenticationUtils.getAuthenticatedUserId();

        log.info("User {} requested details for order {}", userId, orderId);

        Order order = orderService.getOrder(orderId);

        // Verify ownership
        if (!order.getUserId().equals(userId)) {
            log.warn("User {} attempted to access order {} which belongs to user {}",
                    userId, orderId, order.getUserId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        OrderResponse response = mapToResponse(order);

        log.info("Order details retrieved for user {}: Order ID {} - {} {} {}",
                userId, orderId, order.getOrderType(), order.getAmount(), order.getStatus());
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> cancelOrder(
            @PathVariable Long orderId) {
        Long userId = authenticationUtils.getAuthenticatedUserId();


        log.info("User {} is cancelling order {}", userId, orderId);

        Order order = orderService.getOrder(orderId);

        // Verify ownership
        if (!order.getUserId().equals(userId)) {
            log.warn("User {} attempted to cancel order {} which belongs to user {}",
                    userId, orderId, order.getUserId());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        orderService.cancelOrder(orderId);

        log.info("Order cancelled successfully for user {}: Order ID {}", userId, orderId);
        return ResponseEntity.noContent().build();
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
}

