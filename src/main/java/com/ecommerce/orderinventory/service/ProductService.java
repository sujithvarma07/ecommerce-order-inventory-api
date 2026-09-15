package com.ecommerce.orderinventory.service;

import com.ecommerce.orderinventory.dto.ProductRequest;
import com.ecommerce.orderinventory.dto.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ProductService {

    ProductResponse create(ProductRequest request);

    ProductResponse getById(Long id);

    Page<ProductResponse> getAll(Pageable pageable);

    Page<ProductResponse> getByCategory(Long categoryId, Pageable pageable);

    ProductResponse update(Long id, ProductRequest request);

    void delete(Long id);

    ProductResponse adjustStock(Long id, int delta);

    List<ProductResponse> getLowStock(int threshold);
}
