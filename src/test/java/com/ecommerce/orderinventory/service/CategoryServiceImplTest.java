package com.ecommerce.orderinventory.service;

import com.ecommerce.orderinventory.dto.CategoryRequest;
import com.ecommerce.orderinventory.dto.CategoryResponse;
import com.ecommerce.orderinventory.entity.Category;
import com.ecommerce.orderinventory.exception.DuplicateResourceException;
import com.ecommerce.orderinventory.exception.ResourceNotFoundException;
import com.ecommerce.orderinventory.repository.CategoryRepository;
import com.ecommerce.orderinventory.service.impl.CategoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoryServiceImplTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private Category category;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setId(1L);
        category.setName("Electronics");
        category.setDescription("Electronic gadgets and accessories");
    }

    @Test
    void create_savesCategory_whenNameIsUnique() {
        CategoryRequest request = new CategoryRequest("Electronics", "Electronic gadgets and accessories");
        when(categoryRepository.existsByNameIgnoreCase("Electronics")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenReturn(category);

        CategoryResponse response = categoryService.create(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Electronics");
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void create_throwsConflict_whenNameAlreadyExists() {
        CategoryRequest request = new CategoryRequest("Electronics", "duplicate");
        when(categoryRepository.existsByNameIgnoreCase("Electronics")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.create(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already exists");

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void getById_returnsCategory_whenFound() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        CategoryResponse response = categoryService.getById(1L);

        assertThat(response.getName()).isEqualTo("Electronics");
    }

    @Test
    void getById_throwsNotFound_whenMissing() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void getAll_returnsMappedPage() {
        Pageable pageable = PageRequest.of(0, 20);
        when(categoryRepository.findAll(pageable)).thenReturn(new PageImpl<>(List.of(category), pageable, 1));

        Page<CategoryResponse> responses = categoryService.getAll(pageable);

        assertThat(responses.getTotalElements()).isEqualTo(1);
        assertThat(responses.getContent().get(0).getName()).isEqualTo("Electronics");
    }

    @Test
    void delete_removesCategory_whenFound() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        categoryService.delete(1L);

        verify(categoryRepository).delete(category);
    }

    @Test
    void delete_throwsNotFound_whenMissing() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");

        verify(categoryRepository, never()).delete(any());
    }

    @Test
    void update_updatesCategory_whenFound() {
        CategoryRequest request = new CategoryRequest("Electronics & Gadgets", "Updated description");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CategoryResponse response = categoryService.update(1L, request);

        assertThat(response.getName()).isEqualTo("Electronics & Gadgets");
        assertThat(response.getDescription()).isEqualTo("Updated description");
        assertThat(category.getName()).isEqualTo("Electronics & Gadgets");
    }

    @Test
    void update_throwsNotFound_whenMissing() {
        CategoryRequest request = new CategoryRequest("Doesn't matter", null);
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.update(99L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not found");

        verify(categoryRepository, never()).save(any());
    }
}
