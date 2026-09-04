package com.ecommerce.orderinventory.service;

import com.ecommerce.orderinventory.dto.OrderItemRequest;
import com.ecommerce.orderinventory.dto.OrderRequest;
import com.ecommerce.orderinventory.dto.OrderResponse;
import com.ecommerce.orderinventory.entity.Order;
import com.ecommerce.orderinventory.entity.OrderStatus;
import com.ecommerce.orderinventory.entity.Product;
import com.ecommerce.orderinventory.exception.InsufficientStockException;
import com.ecommerce.orderinventory.exception.ResourceNotFoundException;
import com.ecommerce.orderinventory.repository.OrderRepository;
import com.ecommerce.orderinventory.repository.ProductRepository;
import com.ecommerce.orderinventory.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private OrderServiceImpl orderService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = new Product();
        product.setId(1L);
        product.setName("Wireless Mouse");
        product.setSku("WM-1001");
        product.setPrice(new BigDecimal("20.00"));
        product.setStockQuantity(10);
    }

    @Test
    void create_computesTotalAndPersistsOrder_whenStockIsSufficient() {
        OrderRequest request = new OrderRequest("Jane Doe", "jane@example.com",
                List.of(new OrderItemRequest(1L, 3)));

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.create(request);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getTotalAmount()).isEqualByComparingTo("60.00");
        assertThat(response.getItems()).hasSize(1);
        assertThat(response.getItems().get(0).getSubtotal()).isEqualByComparingTo("60.00");
    }

    @Test
    void create_throwsConflict_whenStockIsInsufficient() {
        OrderRequest request = new OrderRequest("Jane Doe", "jane@example.com",
                List.of(new OrderItemRequest(1L, 50)));

        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Insufficient stock");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void create_throwsNotFound_whenProductMissing() {
        OrderRequest request = new OrderRequest("Jane Doe", "jane@example.com",
                List.of(new OrderItemRequest(99L, 1)));

        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Product not found");
    }

    @Test
    void updateStatus_changesStatus_whenOrderExists() {
        Order order = new Order();
        order.setId(5L);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(BigDecimal.TEN);

        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.updateStatus(5L, OrderStatus.CONFIRMED);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
    }

    @Test
    void getById_throwsNotFound_whenMissing() {
        when(orderRepository.findById(123L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getById(123L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");
    }
}
