package com.ecommerce.orderinventory.service;

import com.ecommerce.orderinventory.dto.OrderItemRequest;
import com.ecommerce.orderinventory.dto.OrderRequest;
import com.ecommerce.orderinventory.dto.OrderResponse;
import com.ecommerce.orderinventory.entity.Order;
import com.ecommerce.orderinventory.entity.OrderItem;
import com.ecommerce.orderinventory.entity.OrderStatus;
import com.ecommerce.orderinventory.entity.Product;
import com.ecommerce.orderinventory.exception.InsufficientStockException;
import com.ecommerce.orderinventory.exception.InvalidOrderStateException;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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

        assertThat(product.getStockQuantity()).isEqualTo(7);
        verify(productRepository).save(product);
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
    void getAll_returnsMappedPage() {
        Order order = new Order();
        order.setId(5L);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(BigDecimal.TEN);

        Pageable pageable = PageRequest.of(0, 20);
        when(orderRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(order), pageable, 1));

        Page<OrderResponse> response = orderService.getAll(pageable);

        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.getContent().get(0).getId()).isEqualTo(5L);
    }

    @Test
    void updateStatus_rejectsInvalidTransition() {
        Order order = new Order();
        order.setId(6L);
        order.setStatus(OrderStatus.SHIPPED);
        order.setTotalAmount(BigDecimal.TEN);

        when(orderRepository.findById(6L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateStatus(6L, OrderStatus.PENDING))
                .isInstanceOf(InvalidOrderStateException.class)
                .hasMessageContaining("Cannot move order");

        verify(orderRepository, never()).save(any());
    }

    @Test
    void updateStatus_restoresStock_whenOrderIsCancelled() {
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(4);
        item.setUnitPrice(product.getPrice());
        item.setSubtotal(product.getPrice().multiply(BigDecimal.valueOf(4)));

        Order order = new Order();
        order.setId(7L);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(BigDecimal.valueOf(80));
        order.addItem(item);

        when(orderRepository.findById(7L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.updateStatus(7L, OrderStatus.CANCELLED);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(product.getStockQuantity()).isEqualTo(14);
        verify(productRepository).save(product);
    }

    @Test
    void getById_throwsNotFound_whenMissing() {
        when(orderRepository.findById(123L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getById(123L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void delete_removesOrder_andRestoresStock_whenPending() {
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(3);
        item.setUnitPrice(product.getPrice());
        item.setSubtotal(product.getPrice().multiply(BigDecimal.valueOf(3)));

        Order order = new Order();
        order.setId(8L);
        order.setStatus(OrderStatus.PENDING);
        order.setTotalAmount(BigDecimal.valueOf(60));
        order.addItem(item);

        when(orderRepository.findById(8L)).thenReturn(Optional.of(order));

        orderService.delete(8L);

        assertThat(product.getStockQuantity()).isEqualTo(13);
        verify(productRepository).save(product);
        verify(orderRepository).delete(order);
    }

    @Test
    void delete_removesOrder_withoutRestoringStock_whenAlreadyShipped() {
        OrderItem item = new OrderItem();
        item.setProduct(product);
        item.setQuantity(3);
        item.setUnitPrice(product.getPrice());
        item.setSubtotal(product.getPrice().multiply(BigDecimal.valueOf(3)));

        Order order = new Order();
        order.setId(9L);
        order.setStatus(OrderStatus.SHIPPED);
        order.setTotalAmount(BigDecimal.valueOf(60));
        order.addItem(item);

        when(orderRepository.findById(9L)).thenReturn(Optional.of(order));

        orderService.delete(9L);

        assertThat(product.getStockQuantity()).isEqualTo(10);
        verify(productRepository, never()).save(any());
        verify(orderRepository).delete(order);
    }

    @Test
    void delete_throwsNotFound_whenMissing() {
        when(orderRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.delete(404L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");

        verify(orderRepository, never()).delete(any());
    }
}
