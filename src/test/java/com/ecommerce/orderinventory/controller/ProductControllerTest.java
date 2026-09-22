package com.ecommerce.orderinventory.controller;

import com.ecommerce.orderinventory.dto.ProductRequest;
import com.ecommerce.orderinventory.dto.ProductResponse;
import com.ecommerce.orderinventory.dto.StockAdjustmentRequest;
import com.ecommerce.orderinventory.exception.InsufficientStockException;
import com.ecommerce.orderinventory.exception.ResourceNotFoundException;
import com.ecommerce.orderinventory.service.ProductService;
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
 * Web-layer slice tests for ProductController — request validation, status codes, and response
 * shape, with the service layer mocked out. Security filters are disabled (covered separately by
 * the integration tests).
 */
@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    private ProductResponse sampleResponse() {
        return ProductResponse.builder()
                .id(1L)
                .name("Wireless Mouse")
                .sku("WM-1001")
                .price(new BigDecimal("19.99"))
                .stockQuantity(150)
                .categoryId(1L)
                .categoryName("Electronics")
                .build();
    }

    @Test
    void create_returns201_whenRequestIsValid() throws Exception {
        ProductRequest request = new ProductRequest("Wireless Mouse", "2.4GHz mouse", "WM-1001",
                new BigDecimal("19.99"), 150, 1L);
        when(productService.create(any(ProductRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("WM-1001"));
    }

    @Test
    void create_returns400_withFieldErrors_whenPriceIsMissing() throws Exception {
        String invalidJson = "{\"name\":\"Wireless Mouse\",\"sku\":\"WM-1001\",\"stockQuantity\":150,\"categoryId\":1}";

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.price").exists());
    }

    @Test
    void getById_returns404_whenMissing() throws Exception {
        when(productService.getById(42L)).thenThrow(new ResourceNotFoundException("Product not found with id: 42"));

        mockMvc.perform(get("/api/v1/products/42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Product not found with id: 42"));
    }

    @Test
    void getAll_returnsPagedBody() throws Exception {
        when(productService.getAll(any())).thenReturn(new PageImpl<>(List.of(sampleResponse()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].sku").value("WM-1001"));
    }

    @Test
    void getAll_delegatesToCategoryFilter_whenCategoryIdProvided() throws Exception {
        when(productService.getByCategory(eq(1L), any()))
                .thenReturn(new PageImpl<>(List.of(sampleResponse()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/products").param("categoryId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].categoryId").value(1));
    }

    @Test
    void adjustStock_returns409_whenResultingStockIsNegative() throws Exception {
        when(productService.adjustStock(1L, -1000))
                .thenThrow(new InsufficientStockException("Cannot adjust stock for product 'Wireless Mouse' by -1000"));

        mockMvc.perform(patch("/api/v1/products/1/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new StockAdjustmentRequest(-1000))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Cannot adjust stock for product 'Wireless Mouse' by -1000"));
    }

    @Test
    void getLowStock_returnsList() throws Exception {
        when(productService.getLowStock(10)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/api/v1/products/low-stock").param("threshold", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("WM-1001"));
    }

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/products/1"))
                .andExpect(status().isNoContent());
    }
}
