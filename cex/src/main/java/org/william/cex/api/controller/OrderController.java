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
import org.william.cex.domain.user.service.UserService;
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

        try {
            String userEmail = authenticationUtils.getAuthenticatedUserEmail();
            Long userId = userService.getUserByEmail(userEmail).getId();
            Order.OrderType orderType = Order.OrderType.valueOf(request.getOrderType().toUpperCase());

            log.info("User {} is creating {} order: {} {} -> {} at price {}",
                    userEmail, orderType, request.getAmount(), request.getBaseCurrency(),
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
                    userEmail, order.getId(), request.getAmount(), request.getBaseCurrency(), request.getPrice());
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Error creating order for user", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(
            @PathVariable Long orderId) {

        try {
            String userEmail = authenticationUtils.getAuthenticatedUserEmail();
            Long userId = userService.getUserByEmail(userEmail).getId();

            log.info("User {} requested details for order {}", userEmail, orderId);

            Order order = orderService.getOrder(orderId);

            // Verify ownership
            if (!order.getUserId().equals(userId)) {
                log.warn("User {} attempted to access order {} which belongs to user {}",
                        userEmail, orderId, order.getUserId());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            OrderResponse response = mapToResponse(order);

            log.info("Order details retrieved for user {}: Order ID {} - {} {} {}",
                    userEmail, orderId, order.getOrderType(), order.getAmount(), order.getStatus());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting order", e);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @DeleteMapping("/{orderId}")
    public ResponseEntity<Void> cancelOrder(
            @PathVariable Long orderId) {

        try {
            String userEmail = authenticationUtils.getAuthenticatedUserEmail();
            Long userId = userService.getUserByEmail(userEmail).getId();

            log.info("User {} is cancelling order {}", userEmail, orderId);

            Order order = orderService.getOrder(orderId);

            // Verify ownership
            if (!order.getUserId().equals(userId)) {
                log.warn("User {} attempted to cancel order {} which belongs to user {}",
                        userEmail, orderId, order.getUserId());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            orderService.cancelOrder(orderId);

            log.info("Order cancelled successfully for user {}: Order ID {}", userEmail, orderId);
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
}

