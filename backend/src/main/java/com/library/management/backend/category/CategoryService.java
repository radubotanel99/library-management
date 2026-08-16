package com.library.management.backend.category;

import com.library.management.backend.book.BookRepository;
import com.library.management.backend.book.BookStatus;
import com.library.management.backend.book.CategoryBookCount;
import com.library.management.backend.category.dto.CategoryRequest;
import com.library.management.backend.category.dto.CategoryResponse;
import com.library.management.backend.common.error.ApiException;
import com.library.management.backend.common.error.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Business rules for categories.
 *
 * <p>Two rules live here rather than in the database ({@code DATA_MODEL.md} §3):
 * a name must be unique among active categories (case-insensitively), and a
 * category holding active books cannot be archived.
 *
 * <p>Archiving is a soft delete. There is no restore: a category archived by
 * mistake is re-created, matching the system this replaces ({@code DATA_MODEL.md}
 * §4.1). {@link #update} therefore never touches {@code deleted} -- the previous
 * system un-archived rows as a side effect of editing, and that is not carried over.
 *
 * <p>The service returns DTOs, so entities stop here and never reach a controller.
 */
@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final BookRepository bookRepository;

    public CategoryService(CategoryRepository categoryRepository, BookRepository bookRepository) {
        this.categoryRepository = categoryRepository;
        this.bookRepository = bookRepository;
    }

    /**
     * All active categories, alphabetically.
     *
     * <p>Book counts come from one grouped query folded into a map, not a count per
     * category: the list is unpaged, so a per-row count would be N+1 queries.
     */
    public List<CategoryResponse> findAll() {
        Map<Long, Long> bookCounts = bookRepository.countBooksByCategory(BookStatus.ACTIVE).stream()
                .collect(Collectors.toMap(CategoryBookCount::categoryId, CategoryBookCount::count));

        return categoryRepository.findAllByDeletedFalseOrderByNameAsc().stream()
                .map(category -> CategoryMapper.toResponse(
                        category, bookCounts.getOrDefault(category.getId(), 0L)))
                .toList();
    }

    public CategoryResponse findById(Long id) {
        Category category = requireActive(id);
        return CategoryMapper.toResponse(category, countActiveBooks(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = normalise(request.name());
        if (categoryRepository.existsByNameIgnoreCaseAndDeletedFalse(name)) {
            throw new ApiException(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS, "name");
        }

        Category category = new Category();
        category.setName(name);
        category.setDescription(normalise(request.description()));

        // A brand new category has no books yet, so the count is known without a query.
        return CategoryMapper.toResponse(save(category), 0L);
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = requireActive(id);

        String name = normalise(request.name());
        if (categoryRepository.existsByNameIgnoreCaseAndDeletedFalseAndIdNot(name, id)) {
            throw new ApiException(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS, "name");
        }

        category.setName(name);
        category.setDescription(normalise(request.description()));

        return CategoryMapper.toResponse(save(category), countActiveBooks(id));
    }

    /**
     * Archives a category, provided no active book still points at it.
     *
     * <p>Soft delete only: books removed from the collection keep their category, so
     * a physical delete would break history (and the FK is {@code NO ACTION}).
     */
    @Transactional
    public void delete(Long id) {
        Category category = requireActive(id);

        if (bookRepository.existsByCategoryIdAndStatus(id, BookStatus.ACTIVE)) {
            throw new ApiException(ErrorCode.CATEGORY_HAS_BOOKS);
        }

        category.setDeleted(true);
        categoryRepository.save(category);
    }

    /** An archived category is indistinguishable from a missing one to the API. */
    private Category requireActive(Long id) {
        return categoryRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ApiException(ErrorCode.CATEGORY_NOT_FOUND));
    }

    private long countActiveBooks(Long categoryId) {
        return bookRepository.countByCategoryIdAndStatus(categoryId, BookStatus.ACTIVE);
    }

    /**
     * Flushes so the partial unique index reports a duplicate name here, inside the
     * service, rather than as a raw database error later.
     *
     * <p>The pre-check above catches ordinary duplicates; this catch is the
     * race-condition net for two concurrent requests that both pass it.
     */
    private Category save(Category category) {
        try {
            return categoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(ErrorCode.CATEGORY_NAME_ALREADY_EXISTS, "name", ex);
        }
    }

    /** Trims incidental whitespace and treats an empty string as absent. */
    private String normalise(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
