package com.library.management.backend.book;

/**
 * How many books of a given status one category holds.
 *
 * <p>A projection, not an entity: it exists so the category list can be built with
 * a single grouped query instead of one count per category.
 *
 * @param categoryId the category the books belong to
 * @param count      number of matching books
 */
public record CategoryBookCount(Long categoryId, long count) {
}
