package com.ecommerce.orderinventory.repository;

import com.ecommerce.orderinventory.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
}
