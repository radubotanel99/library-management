package com.library.management.backend.category;

import com.library.management.backend.category.dto.CategoryResponse;

/**
 * Entity to DTO conversion for categories.
 *
 * <p>Package-private and static: the mapping is a pure function with no state and
 * no dependencies, so making it a Spring bean would buy nothing. Keeping it out of
 * the {@code dto} package and out of public view means only the service can call
 * it -- the entity never gets a chance to leave the service layer.
 *
 * <p>{@code bookCount} is passed in rather than read off the entity: {@code Category}
 * intentionally has no {@code @OneToMany} to books.
 */
final class CategoryMapper {

    private CategoryMapper() {
    }

    static CategoryResponse toResponse(Category category, long bookCount) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                bookCount);
    }
}
