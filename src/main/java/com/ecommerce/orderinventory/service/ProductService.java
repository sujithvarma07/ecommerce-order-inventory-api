package com.ecommerce.orderinventory.service;

import com.ecommerce.orderinventory.dto.ProductRequest;
import com.ecommerce.orderinventory.dto.ProductResponse;

import java.util.List;

public interface ProductService {

    ProductResponse create(ProductRequest request);

    ProductResponse getById(Long id);

    List<ProductResponse> getAll();

    List<ProductResponse> getByCategory(Long categoryId);

    ProductResponse update(Long id, ProductRequest request);

    void delete(Long id);

    ProductResponse adjustStock(Long id, int delta);

    List<ProductResponse> getLowStock(int threshold);
}
