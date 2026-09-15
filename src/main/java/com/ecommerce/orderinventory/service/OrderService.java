package com.ecommerce.orderinventory.service;

import com.ecommerce.orderinventory.dto.OrderRequest;
import com.ecommerce.orderinventory.dto.OrderResponse;
import com.ecommerce.orderinventory.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {

    OrderResponse create(OrderRequest request);

    OrderResponse getById(Long id);

    Page<OrderResponse> getAll(Pageable pageable);

    OrderResponse updateStatus(Long id, OrderStatus status);

    void delete(Long id);
}
