package com.library.management.backend.book.dto;

import com.library.management.backend.book.RemovalReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Removal payload for {@code POST /api/books/{id}/remove}
 * ({@code API_CONTRACT.md} §5).
 *
 * <p>A {@code POST} to a sub-resource rather than a {@code DELETE} because a reason
 * is mandatory and {@code DELETE} has no body semantics for one.
 *
 * @param status     the reason the copy is leaving the collection. Named
 *                   {@code status} because that is the wire field in the contract,
 *                   but typed as {@link RemovalReason}, which has no {@code ACTIVE}
 *                   constant -- so removal can never be used to restore a copy
 * @param removalNote optional free-text detail, bounded to what the column holds
 */
public record BookRemoveRequest(
        @NotNull RemovalReason status,
        @Size(max = 500) String removalNote) {
}
