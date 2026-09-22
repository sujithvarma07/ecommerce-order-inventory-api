package com.ecommerce.orderinventory.controller;

import com.ecommerce.orderinventory.dto.CategoryRequest;
import com.ecommerce.orderinventory.dto.CategoryResponse;
import com.ecommerce.orderinventory.exception.ResourceNotFoundException;
import com.ecommerce.orderinventory.service.CategoryService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer slice tests: request validation, status codes, and response shape for the
 * controller <-> DTO <-> exception-handler wiring, independent of business logic (already
 * covered by the service-layer unit tests) and independent of security (covered separately by
 * the integration tests under src/test/.../integration). Security filters are disabled here so
 * these stay focused on the controller layer itself.
 */
@WebMvcTest(CategoryController.class)
@AutoConfigureMockMvc(addFilters = false)
class CategoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CategoryService categoryService;

    @Test
    void create_returns201_whenRequestIsValid() throws Exception {
        CategoryResponse response = CategoryResponse.builder().id(1L).name("Electronics").build();
        when(categoryService.create(any(CategoryRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("Electronics", "Gadgets"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Electronics"));
    }

    @Test
    void create_returns400_withFieldErrors_whenNameIsBlank() throws Exception {
        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("", "Gadgets"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.name").exists());
    }

    @Test
    void getById_returns404_withStandardErrorShape_whenMissing() throws Exception {
        when(categoryService.getById(99L)).thenThrow(new ResourceNotFoundException("Category not found with id: 99"));

        mockMvc.perform(get("/api/v1/categories/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Category not found with id: 99"))
                .andExpect(jsonPath("$.path").value("/api/v1/categories/99"));
    }

    @Test
    void getAll_returnsPagedBody() throws Exception {
        CategoryResponse response = CategoryResponse.builder().id(1L).name("Electronics").build();
        when(categoryService.getAll(any())).thenReturn(new PageImpl<>(List.of(response), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Electronics"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void delete_returns204_whenFound() throws Exception {
        mockMvc.perform(delete("/api/v1/categories/1"))
                .andExpect(status().isNoContent());
    }
}
