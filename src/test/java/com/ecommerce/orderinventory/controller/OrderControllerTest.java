package com.ecommerce.orderinventory.controller;

import com.ecommerce.orderinventory.dto.OrderItemRequest;
import com.ecommerce.orderinventory.dto.OrderRequest;
import com.ecommerce.orderinventory.dto.OrderResponse;
import com.ecommerce.orderinventory.entity.OrderStatus;
import com.ecommerce.orderinventory.exception.InvalidOrderStateException;
import com.ecommerce.orderinventory.exception.ResourceNotFoundException;
import com.ecommerce.orderinventory.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer slice tests for OrderController — request validation, status codes, and response
 * shape, with the service layer mocked out. Security filters are disabled (covered separately by
 * the integration tests).
 */
@WebMvcTest(OrderController.class)
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    private OrderResponse sampleResponse(OrderStatus status) {
        return OrderResponse.builder()
                .id(1L)
                .customerName("Jane Doe")
                .customerEmail("jane@example.com")
                .status(status)
                .totalAmount(new BigDecimal("60.00"))
                .items(List.of())
                .build();
    }

    @Test
    void create_returns201_whenRequestIsValid() throws Exception {
        OrderRequest request = new OrderRequest("Jane Doe", "jane@example.com",
                List.of(new OrderItemRequest(1L, 3)));
        when(orderService.create(any(OrderRequest.class))).thenReturn(sampleResponse(OrderStatus.PENDING));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void create_returns400_whenItemsIsEmpty() throws Exception {
        OrderRequest request = new OrderRequest("Jane Doe", "jane@example.com", List.of());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.items").exists());
    }

    @Test
    void create_returns400_whenEmailIsInvalid() throws Exception {
        OrderRequest request = new OrderRequest("Jane Doe", "not-an-email",
                List.of(new OrderItemRequest(1L, 3)));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.customerEmail").exists());
    }

    @Test
    void getById_returns404_whenMissing() throws Exception {
        when(orderService.getById(123L)).thenThrow(new ResourceNotFoundException("Order not found with id: 123"));

        mockMvc.perform(get("/api/v1/orders/123"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order not found with id: 123"));
    }

    @Test
    void getAll_returnsPagedBody() throws Exception {
        when(orderService.getAll(any()))
                .thenReturn(new PageImpl<>(List.of(sampleResponse(OrderStatus.PENDING)), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/orders"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"));
    }

    @Test
    void updateStatus_returns409_onInvalidTransition() throws Exception {
        when(orderService.updateStatus(eq(1L), eq(OrderStatus.PENDING)))
                .thenThrow(new InvalidOrderStateException("Cannot move order from SHIPPED to PENDING"));

        mockMvc.perform(patch("/api/v1/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PENDING\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot move order from SHIPPED to PENDING"));
    }

    @Test
    void updateStatus_returns400_whenStatusIsMissing() throws Exception {
        mockMvc.perform(patch("/api/v1/orders/1/status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.status").exists());
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/orders/1"))
                .andExpect(status().isNoContent());
    }
}
