package com.ecommerce.orderinventory.repository;

import com.ecommerce.orderinventory.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCase(String sku);

    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);

    List<Product> findByStockQuantityLessThanEqual(Integer threshold);
}
