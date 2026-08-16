package com.library.management.backend.common.error;

import org.springframework.http.HttpStatus;

/**
 * Every error the API can return, with the HTTP status it maps to.
 *
 * <p>The constant name <em>is</em> the wire contract: the frontend keys its EN/RO
 * translations off it (see {@code API_CONTRACT.md} §3/§4), so renaming one is a
 * breaking change. The {@code defaultMessage} is developer-facing English only and
 * is never displayed to a user -- the backend never returns translated text.
 *
 * <p>Codes for features not yet built are listed here on purpose: they are already
 * part of the published contract, and having them in one place keeps the mapping
 * from code to status in a single file.
 */
public enum ErrorCode {

    // --- Book ---
    BOOK_NUMBER_ALREADY_EXISTS(HttpStatus.CONFLICT, "A book with this number already exists"),
    BOOK_ALREADY_ON_LOAN(HttpStatus.CONFLICT, "This copy is already on loan"),
    BOOK_HAS_OPEN_LOAN(HttpStatus.CONFLICT, "This copy has an open loan"),
    BOOK_NOT_REMOVED(HttpStatus.CONFLICT, "This copy is already active"),
    BOOK_NUMBER_TAKEN_ON_RESTORE(HttpStatus.CONFLICT, "The book number is now used by another active book"),
    BOOK_NOT_FOUND(HttpStatus.NOT_FOUND, "Book not found"),

    // --- Member ---
    MEMBER_TOO_MANY_LOANS(HttpStatus.CONFLICT, "Member has reached the maximum number of open loans"),
    MEMBER_NAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "A member with this name already exists"),
    MEMBER_HAS_OPEN_LOANS(HttpStatus.CONFLICT, "Member still has open loans"),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "Member not found"),

    // --- Category ---
    CATEGORY_NAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "A category with this name already exists"),
    CATEGORY_HAS_BOOKS(HttpStatus.CONFLICT, "Category still has active books"),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "Category not found"),

    // --- Loan ---
    LOAN_ALREADY_FINISHED(HttpStatus.CONFLICT, "Loan is already finished"),
    LOAN_NOT_FOUND(HttpStatus.NOT_FOUND, "Loan not found"),

    // --- Parameter ---
    PARAMETER_INVALID_VALUE(HttpStatus.BAD_REQUEST, "Parameter value must be a positive integer"),

    // --- Generic ---
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Request validation failed"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT, "The request conflicts with existing data"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
