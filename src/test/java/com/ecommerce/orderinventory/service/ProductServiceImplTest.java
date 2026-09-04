package com.ecommerce.orderinventory.service;

import com.ecommerce.orderinventory.dto.ProductRequest;
import com.ecommerce.orderinventory.dto.ProductResponse;
import com.ecommerce.orderinventory.entity.Category;
import com.ecommerce.orderinventory.entity.Product;
import com.ecommerce.orderinventory.exception.DuplicateResourceException;
import com.ecommerce.orderinventory.exception.ResourceNotFoundException;
import com.ecommerce.orderinventory.repository.CategoryRepository;
import com.ecommerce.orderinventory.repository.ProductRepository;
import com.ecommerce.orderinventory.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    private Category category;
    private Product product;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setId(1L);
        category.setName("Electronics");

        product = new Product();
        product.setId(1L);
        product.setName("Wireless Mouse");
        product.setSku("WM-1001");
        product.setPrice(new BigDecimal("19.99"));
        product.setStockQuantity(150);
        product.setCategory(category);
    }

    @Test
    void create_savesProduct_whenSkuIsUniqueAndCategoryExists() {
        ProductRequest request = new ProductRequest("Wireless Mouse", "2.4GHz mouse", "WM-1001",
                new BigDecimal("19.99"), 150, 1L);

        when(productRepository.existsBySkuIgnoreCase("WM-1001")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenReturn(product);

        ProductResponse response = productService.create(request);

        assertThat(response.getSku()).isEqualTo("WM-1001");
        assertThat(response.getCategoryName()).isEqualTo("Electronics");
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void create_throwsConflict_whenSkuAlreadyExists() {
        ProductRequest request = new ProductRequest("Wireless Mouse", "dup", "WM-1001",
                new BigDecimal("19.99"), 150, 1L);

        when(productRepository.existsBySkuIgnoreCase("WM-1001")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");

        verify(productRepository, never()).save(any());
    }

    @Test
    void create_throwsNotFound_whenCategoryMissing() {
        ProductRequest request = new ProductRequest("Wireless Mouse", "desc", "WM-1002",
                new BigDecimal("19.99"), 150, 99L);

        when(productRepository.existsBySkuIgnoreCase("WM-1002")).thenReturn(false);
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Category not found");
    }

    @Test
    void getById_returnsProduct_whenFound() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        ProductResponse response = productService.getById(1L);

        assertThat(response.getName()).isEqualTo("Wireless Mouse");
        assertThat(response.getCategoryId()).isEqualTo(1L);
    }

    @Test
    void getById_throwsNotFound_whenMissing() {
        when(productRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getById(42L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void delete_removesProduct_whenFound() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        productService.delete(1L);

        verify(productRepository).delete(product);
    }
}
