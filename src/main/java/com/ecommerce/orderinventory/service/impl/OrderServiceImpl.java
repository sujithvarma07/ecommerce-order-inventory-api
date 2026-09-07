package com.ecommerce.orderinventory.service.impl;

import com.ecommerce.orderinventory.dto.*;
import com.ecommerce.orderinventory.entity.Order;
import com.ecommerce.orderinventory.entity.OrderItem;
import com.ecommerce.orderinventory.entity.OrderStatus;
import com.ecommerce.orderinventory.entity.Product;
import com.ecommerce.orderinventory.exception.InsufficientStockException;
import com.ecommerce.orderinventory.exception.InvalidOrderStateException;
import com.ecommerce.orderinventory.exception.ResourceNotFoundException;
import com.ecommerce.orderinventory.repository.OrderRepository;
import com.ecommerce.orderinventory.repository.ProductRepository;
import com.ecommerce.orderinventory.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(OrderStatus.PENDING, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.SHIPPED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    @Override
    @Transactional
    public OrderResponse create(OrderRequest request) {
        Order order = new Order();
        order.setCustomerName(request.getCustomerName());
        order.setCustomerEmail(request.getCustomerEmail());
        order.setStatus(OrderStatus.PENDING);

        BigDecimal total = BigDecimal.ZERO;

        for (OrderItemRequest itemRequest : request.getItems()) {
            Product product = productRepository.findById(itemRequest.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Product not found with id: " + itemRequest.getProductId()));

            if (product.getStockQuantity() < itemRequest.getQuantity()) {
                throw new InsufficientStockException(
                        "Insufficient stock for product '" + product.getName() + "' (requested "
                                + itemRequest.getQuantity() + ", available " + product.getStockQuantity() + ")");
            }

            product.setStockQuantity(product.getStockQuantity() - itemRequest.getQuantity());
            productRepository.save(product);

            OrderItem item = new OrderItem();
            item.setProduct(product);
            item.setQuantity(itemRequest.getQuantity());
            item.setUnitPrice(product.getPrice());
            item.setSubtotal(product.getPrice().multiply(BigDecimal.valueOf(itemRequest.getQuantity())));

            order.addItem(item);
            total = total.add(item.getSubtotal());
        }

        order.setTotalAmount(total);

        Order saved = orderRepository.save(order);
        log.info("Created order id={} for customerEmail={} total={}", saved.getId(),
                saved.getCustomerEmail(), saved.getTotalAmount());
        return toResponse(saved);
    }

    @Override
    public OrderResponse getById(Long id) {
        return toResponse(findOrderOrThrow(id));
    }

    @Override
    public List<OrderResponse> getAll() {
        return orderRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public OrderResponse updateStatus(Long id, OrderStatus newStatus) {
        Order order = findOrderOrThrow(id);
        OrderStatus currentStatus = order.getStatus();

        if (currentStatus == newStatus) {
            return toResponse(order);
        }

        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(currentStatus, EnumSet.noneOf(OrderStatus.class));
        if (!allowed.contains(newStatus)) {
            throw new InvalidOrderStateException(
                    "Cannot move order from " + currentStatus + " to " + newStatus);
        }

        if (newStatus == OrderStatus.CANCELLED) {
            restoreStock(order);
        }

        order.setStatus(newStatus);
        Order updated = orderRepository.save(order);
        log.info("Order id={} status changed {} -> {}", id, currentStatus, newStatus);
        return toResponse(updated);
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Order order = findOrderOrThrow(id);
        if (order.getStatus() == OrderStatus.PENDING || order.getStatus() == OrderStatus.CONFIRMED) {
            restoreStock(order);
        }
        orderRepository.delete(order);
        log.info("Deleted order id={}", id);
    }

    private void restoreStock(Order order) {
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            product.setStockQuantity(product.getStockQuantity() + item.getQuantity());
            productRepository.save(product);
        }
    }

    private Order findOrderOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + id));
    }

    private OrderResponse toResponse(Order order) {
        List<OrderItemResponse> itemResponses = order.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .quantity(item.getQuantity())
                        .unitPrice(item.getUnitPrice())
                        .subtotal(item.getSubtotal())
                        .build())
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .customerName(order.getCustomerName())
                .customerEmail(order.getCustomerEmail())
                .status(order.getStatus())
                .totalAmount(order.getTotalAmount())
                .items(itemResponses)
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
