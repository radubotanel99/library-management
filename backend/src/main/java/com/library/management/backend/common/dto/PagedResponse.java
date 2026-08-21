package com.library.management.backend.common.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * The paged envelope every list endpoint returns ({@code API_CONTRACT.md} §5).
 *
 * <p>It exists because Spring's {@code PageImpl} does not serialise to the shape
 * the contract publishes: its JSON exposes {@code number}, {@code numberOfElements},
 * {@code pageable}, {@code sort} and more, and that shape is a Spring
 * implementation detail rather than a contract. Mapping onto this record keeps the
 * wire format stable no matter what Spring Data does with {@code Page}.
 *
 * <p>Lives in {@code common} because members and loans page identically.
 *
 * @param content       the rows on this page
 * @param page          zero-based page index (Spring's {@code number})
 * @param size          requested page size
 * @param totalElements matching rows across every page
 * @param totalPages    number of pages at this size
 */
public record PagedResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static <T> PagedResponse<T> of(Page<T> page) {
        return new PagedResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }
}
