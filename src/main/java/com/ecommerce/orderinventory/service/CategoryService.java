package com.ecommerce.orderinventory.service;

import com.ecommerce.orderinventory.dto.CategoryRequest;
import com.ecommerce.orderinventory.dto.CategoryResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface CategoryService {

    CategoryResponse create(CategoryRequest request);

    CategoryResponse getById(Long id);

    Page<CategoryResponse> getAll(Pageable pageable);

    CategoryResponse update(Long id, CategoryRequest request);

    void delete(Long id);
}
