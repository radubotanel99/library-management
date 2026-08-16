package com.library.management.backend.category;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.library.management.backend.book.BookRepository;
import com.library.management.backend.book.BookStatus;
import com.library.management.backend.book.CategoryBookCount;
import com.library.management.backend.category.dto.CategoryRequest;
import com.library.management.backend.category.dto.CategoryResponse;
import com.library.management.backend.common.error.ApiException;
import com.library.management.backend.common.error.ErrorCode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * Unit tests for the category business rules.
 *
 * <p>Plain JUnit + Mockito, no Spring context: these rules are pure logic over two
 * repositories, so a container would only make the suite slower.
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private CategoryService categoryService;

    private static Category category(Long id, String name) {
        Category category = new Category();
        category.setId(id);
        category.setName(name);
        category.setDescription("Novels and short stories");
        return category;
    }

    @Nested
    @DisplayName("findAll")
    class FindAll {

        @Test
        void returnsActiveCategoriesWithTheirActiveBookCounts() {
            when(categoryRepository.findAllByDeletedFalseOrderByNameAsc())
                    .thenReturn(List.of(category(1L, "Fiction"), category(2L, "History")));
            when(bookRepository.countBooksByCategory(BookStatus.ACTIVE))
                    .thenReturn(List.of(new CategoryBookCount(1L, 42L)));

            List<CategoryResponse> result = categoryService.findAll();

            assertThat(result).extracting(CategoryResponse::name, CategoryResponse::bookCount)
                    .containsExactly(tuple("Fiction", 42L), tuple("History", 0L));
        }
    }

    @Nested
    @DisplayName("findById")
    class FindById {

        @Test
        void returnsTheCategoryWithItsActiveBookCount() {
            when(categoryRepository.findByIdAndDeletedFalse(1L))
                    .thenReturn(Optional.of(category(1L, "Fiction")));
            when(bookRepository.countByCategoryIdAndStatus(1L, BookStatus.ACTIVE)).thenReturn(7L);

            CategoryResponse result = categoryService.findById(1L);

            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.name()).isEqualTo("Fiction");
            assertThat(result.bookCount()).isEqualTo(7L);
        }

        @Test
        void throwsNotFoundWhenMissingOrArchived() {
            when(categoryRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.findById(99L))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("create")
    class Create {

        @Test
        void savesTrimmedNameAndReportsZeroBooks() {
            when(categoryRepository.existsByNameIgnoreCaseAndDeletedFalse("Fiction")).thenReturn(false);
            when(categoryRepository.saveAndFlush(any(Category.class)))
                    .thenAnswer(invocation -> {
                        Category saved = invocation.getArgument(0);
                        saved.setId(5L);
                        return saved;
                    });

            CategoryResponse result = categoryService.create(new CategoryRequest("  Fiction  ", " Novels "));

            ArgumentCaptor<Category> captor = ArgumentCaptor.forClass(Category.class);
            verify(categoryRepository).saveAndFlush(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("Fiction");
            assertThat(captor.getValue().getDescription()).isEqualTo("Novels");
            assertThat(captor.getValue().isDeleted()).isFalse();

            assertThat(result.id()).isEqualTo(5L);
            assertThat(result.bookCount()).isZero();
        }

        @Test
        void throwsWhenAnActiveCategoryAlreadyUsesTheName() {
            when(categoryRepository.existsByNameIgnoreCaseAndDeletedFalse("Fiction")).thenReturn(true);

            assertThatThrownBy(() -> categoryService.create(new CategoryRequest("Fiction", null)))
                    .isInstanceOf(ApiException.class)
                    .satisfies(thrown -> {
                        ApiException ex = (ApiException) thrown;
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS);
                        assertThat(ex.getField()).isEqualTo("name");
                    });

            verify(categoryRepository, never()).saveAndFlush(any());
        }

        @Test
        void mapsAConcurrentUniqueIndexViolationOntoTheSameCode() {
            when(categoryRepository.existsByNameIgnoreCaseAndDeletedFalse("Fiction")).thenReturn(false);
            when(categoryRepository.saveAndFlush(any(Category.class)))
                    .thenThrow(new DataIntegrityViolationException("ux_category_name_active"));

            assertThatThrownBy(() -> categoryService.create(new CategoryRequest("Fiction", null)))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS);
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        void changesNameAndDescriptionOnly() {
            Category existing = category(1L, "Fiction");
            when(categoryRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(existing));
            when(categoryRepository.existsByNameIgnoreCaseAndDeletedFalseAndIdNot("Novels", 1L)).thenReturn(false);
            when(categoryRepository.saveAndFlush(existing)).thenReturn(existing);
            when(bookRepository.countByCategoryIdAndStatus(1L, BookStatus.ACTIVE)).thenReturn(3L);

            CategoryResponse result = categoryService.update(1L, new CategoryRequest("Novels", "Long fiction"));

            assertThat(existing.getName()).isEqualTo("Novels");
            assertThat(existing.getDescription()).isEqualTo("Long fiction");
            assertThat(result.bookCount()).isEqualTo(3L);
        }

        @Test
        void neverUnArchivesTheCategory() {
            Category existing = category(1L, "Fiction");
            existing.setDeleted(false);
            when(categoryRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(existing));
            when(categoryRepository.existsByNameIgnoreCaseAndDeletedFalseAndIdNot("Novels", 1L)).thenReturn(false);
            when(categoryRepository.saveAndFlush(existing)).thenReturn(existing);

            categoryService.update(1L, new CategoryRequest("Novels", null));

            // Restore is explicit and categories have none: an edit must not touch the flag.
            assertThat(existing.isDeleted()).isFalse();
        }

        @Test
        void ignoresTheCategoryItselfWhenCheckingForDuplicates() {
            Category existing = category(1L, "Fiction");
            when(categoryRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(existing));
            when(categoryRepository.existsByNameIgnoreCaseAndDeletedFalseAndIdNot("Fiction", 1L)).thenReturn(false);
            when(categoryRepository.saveAndFlush(existing)).thenReturn(existing);

            categoryService.update(1L, new CategoryRequest("Fiction", "Same name, new description"));

            verify(categoryRepository, never()).existsByNameIgnoreCaseAndDeletedFalse(any());
        }

        @Test
        void throwsWhenAnotherActiveCategoryUsesTheName() {
            when(categoryRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(category(1L, "Fiction")));
            when(categoryRepository.existsByNameIgnoreCaseAndDeletedFalseAndIdNot("History", 1L)).thenReturn(true);

            assertThatThrownBy(() -> categoryService.update(1L, new CategoryRequest("History", null)))
                    .isInstanceOf(ApiException.class)
                    .satisfies(thrown -> {
                        ApiException ex = (ApiException) thrown;
                        assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS);
                        assertThat(ex.getField()).isEqualTo("name");
                    });

            verify(categoryRepository, never()).saveAndFlush(any());
        }

        @Test
        void throwsNotFoundWhenMissingOrArchived() {
            when(categoryRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.update(99L, new CategoryRequest("Fiction", null)))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        void softDeletesAnEmptyCategory() {
            Category existing = category(1L, "Fiction");
            when(categoryRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(existing));
            when(bookRepository.existsByCategoryIdAndStatus(1L, BookStatus.ACTIVE)).thenReturn(false);

            categoryService.delete(1L);

            assertThat(existing.isDeleted()).isTrue();
            verify(categoryRepository).save(existing);
            verify(categoryRepository, never()).delete(any());
        }

        @Test
        void throwsWhenTheCategoryStillHasActiveBooks() {
            Category existing = category(1L, "Fiction");
            when(categoryRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(existing));
            when(bookRepository.existsByCategoryIdAndStatus(1L, BookStatus.ACTIVE)).thenReturn(true);

            assertThatThrownBy(() -> categoryService.delete(1L))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.CATEGORY_HAS_BOOKS);

            assertThat(existing.isDeleted()).isFalse();
            verify(categoryRepository, never()).save(any());
        }

        @Test
        void throwsNotFoundWhenMissingOrArchived() {
            when(categoryRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> categoryService.delete(99L))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);

            verify(bookRepository, never()).existsByCategoryIdAndStatus(eq(99L), any());
        }
    }
}
