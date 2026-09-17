package com.ecommerce.orderinventory.integration;

import com.ecommerce.orderinventory.dto.*;
import com.ecommerce.orderinventory.entity.OrderStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end check that authentication, authorization, order placement, and inventory
 * deduction/restoration all work together correctly through the real HTTP layer (not mocks):
 * public catalog reads, admin-only catalog/fulfillment writes, authenticated-but-not-admin order
 * placement, and stock moving down on order creation and back up on cancellation.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"dev", "test"})
class OrderInventoryFlowIntegrationTest {

    @LocalServerPort
    private int port;

    @Value("${app.security.admin-username}")
    private String adminUsername;

    @Value("${app.security.admin-password}")
    private String adminPassword;

    @Value("${app.security.customer-username}")
    private String customerUsername;

    @Value("${app.security.customer-password}")
    private String customerPassword;

    private final TestRestTemplate restTemplate = new TestRestTemplate();

    private String baseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    void authenticationAuthorizationOrdersAndInventory_workTogetherCorrectly() {
        TestRestTemplate admin = restTemplate.withBasicAuth(adminUsername, adminPassword);
        TestRestTemplate customer = restTemplate.withBasicAuth(customerUsername, customerPassword);

        // Anonymous write is rejected.
        ResponseEntity<ApiTestError> anonymousCreate = restTemplate.postForEntity(
                baseUrl() + "/api/v1/categories", new CategoryRequest("Books", "All books"), ApiTestError.class);
        assertThat(anonymousCreate.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // A logged-in customer isn't allowed to manage the catalog.
        ResponseEntity<ApiTestError> customerCreateCategory = customer.postForEntity(
                baseUrl() + "/api/v1/categories", new CategoryRequest("Books", "All books"), ApiTestError.class);
        assertThat(customerCreateCategory.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Admin sets up the catalog.
        ResponseEntity<CategoryResponse> categoryResponse = admin.postForEntity(
                baseUrl() + "/api/v1/categories", new CategoryRequest("Books", "All books"), CategoryResponse.class);
        assertThat(categoryResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long categoryId = categoryResponse.getBody().getId();

        ProductRequest productRequest = new ProductRequest(
                "Clean Code", "A handbook of agile software craftsmanship", "BK-2001",
                new BigDecimal("34.99"), 20, categoryId);
        ResponseEntity<ProductResponse> productResponse = admin.postForEntity(
                baseUrl() + "/api/v1/products", productRequest, ProductResponse.class);
        assertThat(productResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long productId = productResponse.getBody().getId();
        assertThat(productResponse.getBody().getStockQuantity()).isEqualTo(20);

        // Anyone can browse the catalog.
        ResponseEntity<ProductResponse> publicRead = restTemplate.getForEntity(
                baseUrl() + "/api/v1/products/" + productId, ProductResponse.class);
        assertThat(publicRead.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(publicRead.getBody().getStockQuantity()).isEqualTo(20);

        // Placing an order requires being logged in, but not as an admin.
        OrderRequest orderRequest = new OrderRequest("Jane Doe", "jane@example.com",
                List.of(new OrderItemRequest(productId, 5)));

        ResponseEntity<ApiTestError> anonymousOrder = restTemplate.postForEntity(
                baseUrl() + "/api/v1/orders", orderRequest, ApiTestError.class);
        assertThat(anonymousOrder.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        ResponseEntity<OrderResponse> orderResponse = customer.postForEntity(
                baseUrl() + "/api/v1/orders", orderRequest, OrderResponse.class);
        assertThat(orderResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(orderResponse.getBody().getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(orderResponse.getBody().getTotalAmount()).isEqualByComparingTo("174.95");
        Long orderId = orderResponse.getBody().getId();

        // Stock was actually deducted, visible to anyone browsing the catalog.
        ResponseEntity<ProductResponse> afterOrder = restTemplate.getForEntity(
                baseUrl() + "/api/v1/products/" + productId, ProductResponse.class);
        assertThat(afterOrder.getBody().getStockQuantity()).isEqualTo(15);

        // The customer who placed the order can't push it through fulfillment themselves.
        ResponseEntity<ApiTestError> customerStatusUpdate = customer.exchange(
                baseUrl() + "/api/v1/orders/" + orderId + "/status",
                org.springframework.http.HttpMethod.PATCH,
                new org.springframework.http.HttpEntity<>(new OrderStatusUpdateRequest(OrderStatus.CONFIRMED)),
                ApiTestError.class);
        assertThat(customerStatusUpdate.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        // Staff confirms, then cancels — cancellation restores the stock that was deducted.
        ResponseEntity<OrderResponse> confirmed = admin.exchange(
                baseUrl() + "/api/v1/orders/" + orderId + "/status",
                org.springframework.http.HttpMethod.PATCH,
                new org.springframework.http.HttpEntity<>(new OrderStatusUpdateRequest(OrderStatus.CONFIRMED)),
                OrderResponse.class);
        assertThat(confirmed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(confirmed.getBody().getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        ResponseEntity<OrderResponse> cancelled = admin.exchange(
                baseUrl() + "/api/v1/orders/" + orderId + "/status",
                org.springframework.http.HttpMethod.PATCH,
                new org.springframework.http.HttpEntity<>(new OrderStatusUpdateRequest(OrderStatus.CANCELLED)),
                OrderResponse.class);
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelled.getBody().getStatus()).isEqualTo(OrderStatus.CANCELLED);

        ResponseEntity<ProductResponse> afterCancel = restTemplate.getForEntity(
                baseUrl() + "/api/v1/products/" + productId, ProductResponse.class);
        assertThat(afterCancel.getBody().getStockQuantity()).isEqualTo(20);
    }

    /**
     * Minimal shape for deserializing the standardized error body in this test; we only ever
     * assert on the HTTP status for the negative-path calls above.
     */
    private static class ApiTestError {
        public String message;
    }
}
