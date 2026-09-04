package com.ecommerce.orderinventory.service.impl;

import com.ecommerce.orderinventory.dto.ProductRequest;
import com.ecommerce.orderinventory.dto.ProductResponse;
import com.ecommerce.orderinventory.entity.Category;
import com.ecommerce.orderinventory.entity.Product;
import com.ecommerce.orderinventory.exception.DuplicateResourceException;
import com.ecommerce.orderinventory.exception.ResourceNotFoundException;
import com.ecommerce.orderinventory.repository.CategoryRepository;
import com.ecommerce.orderinventory.repository.ProductRepository;
import com.ecommerce.orderinventory.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        if (productRepository.existsBySkuIgnoreCase(request.getSku())) {
            throw new DuplicateResourceException(
                    "Product with SKU '" + request.getSku() + "' already exists");
        }

        Category category = findCategoryOrThrow(request.getCategoryId());

        Product product = new Product();
        applyRequest(product, request, category);

        Product saved = productRepository.save(product);
        return toResponse(saved);
    }

    @Override
    public ProductResponse getById(Long id) {
        return toResponse(findProductOrThrow(id));
    }

    @Override
    public List<ProductResponse> getAll() {
        return productRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public List<ProductResponse> getByCategory(Long categoryId) {
        findCategoryOrThrow(categoryId);
        return productRepository.findByCategoryId(categoryId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = findProductOrThrow(id);
        Category category = findCategoryOrThrow(request.getCategoryId());

        applyRequest(product, request, category);

        Product updated = productRepository.save(product);
        return toResponse(updated);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Product product = findProductOrThrow(id);
        productRepository.delete(product);
    }

    private void applyRequest(Product product, ProductRequest request, Category category) {
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setSku(request.getSku());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setCategory(category);
    }

    private Product findProductOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + id));
    }

    private Category findCategoryOrThrow(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category not found with id: " + categoryId));
    }

    private ProductResponse toResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .sku(product.getSku())
                .price(product.getPrice())
                .stockQuantity(product.getStockQuantity())
                .categoryId(product.getCategory().getId())
                .categoryName(product.getCategory().getName())
                .createdAt(product.getCreatedAt())
                .updatedAt(product.getUpdatedAt())
                .build();
    }
}
