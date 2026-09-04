package com.ecommerce.orderinventory.service;

import com.ecommerce.orderinventory.dto.OrderRequest;
import com.ecommerce.orderinventory.dto.OrderResponse;
import com.ecommerce.orderinventory.entity.OrderStatus;

import java.util.List;

public interface OrderService {

    OrderResponse create(OrderRequest request);

    OrderResponse getById(Long id);

    List<OrderResponse> getAll();

    OrderResponse updateStatus(Long id, OrderStatus status);

    void delete(Long id);
}
