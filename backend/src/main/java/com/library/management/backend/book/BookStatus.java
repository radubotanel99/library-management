package com.library.management.backend.book;

/**
 * Why a copy is or is no longer part of the collection.
 *
 * <p>Declared standalone rather than nested inside {@code Book} so that DTOs can
 * reference it without importing the entity -- entities never leave the service
 * layer.
 *
 * <p>Persisted as a string; the {@code ck_book_status} check constraint mirrors
 * these values in the database.
 */
public enum BookStatus {
    ACTIVE,
    LOST,
    DAMAGED,
    WITHDRAWN
}
