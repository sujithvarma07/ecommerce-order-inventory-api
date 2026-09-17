package com.ecommerce.orderinventory.integration;

import com.ecommerce.orderinventory.entity.Category;
import com.ecommerce.orderinventory.entity.Product;
import com.ecommerce.orderinventory.repository.CategoryRepository;
import com.ecommerce.orderinventory.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that Product.version (@Version, added in Week 2) actually prevents a lost update:
 * two concurrent stock changes that both start from the same version must not both succeed
 * silently. One should win, the other should fail with an optimistic locking exception rather
 * than overwriting the winner's change.
 */
@SpringBootTest
@ActiveProfiles({"dev", "test"})
class ProductOptimisticLockingIntegrationTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long productId;

    @BeforeEach
    void setUp() {
        Category category = new Category();
        category.setName("Concurrency Test Category " + System.nanoTime());
        category.setDescription("Used only by the optimistic locking test");
        Category savedCategory = categoryRepository.save(category);

        Product product = new Product();
        product.setName("Contested Widget");
        product.setDescription("Stock adjusted concurrently by two transactions in this test");
        product.setSku("CONC-" + System.nanoTime());
        product.setPrice(new BigDecimal("9.99"));
        product.setStockQuantity(100);
        product.setCategory(savedCategory);
        productId = productRepository.save(product).getId();
    }

    @Test
    void concurrentStockUpdates_onlyOneSucceeds_otherFailsWithOptimisticLockException() throws InterruptedException {
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        CountDownLatch bothLoaded = new CountDownLatch(2);
        CountDownLatch releaseWriters = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Throwable> failures = Collections.synchronizedList(new java.util.ArrayList<>());

        Runnable decrementStockByOne = () -> {
            try {
                transactionTemplate.executeWithoutResult(status -> {
                    Product product = productRepository.findById(productId).orElseThrow();
                    bothLoaded.countDown();
                    awaitQuietly(releaseWriters);

                    product.setStockQuantity(product.getStockQuantity() - 1);
                    productRepository.saveAndFlush(product);
                });
                successCount.incrementAndGet();
            } catch (Throwable ex) {
                failures.add(ex);
            }
        };

        Thread t1 = new Thread(decrementStockByOne, "stock-writer-1");
        Thread t2 = new Thread(decrementStockByOne, "stock-writer-2");
        t1.start();
        t2.start();

        assertThat(bothLoaded.await(5, TimeUnit.SECONDS)).isTrue();
        releaseWriters.countDown();

        t1.join(5000);
        t2.join(5000);

        assertThat(successCount.get())
                .as("exactly one of the two concurrent updates should win")
                .isEqualTo(1);
        assertThat(failures)
                .as("the losing update should fail rather than silently overwrite the winner")
                .hasSize(1);
        assertThat(isOptimisticLockFailure(failures.get(0)))
                .as("the failure should be an optimistic locking conflict, not something else")
                .isTrue();

        Product finalState = productRepository.findById(productId).orElseThrow();
        assertThat(finalState.getStockQuantity())
                .as("only the winning update's decrement should be reflected — no lost update")
                .isEqualTo(99);
        assertThat(finalState.getVersion()).isEqualTo(1L);
    }

    private void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isOptimisticLockFailure(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            if (current instanceof OptimisticLockingFailureException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
