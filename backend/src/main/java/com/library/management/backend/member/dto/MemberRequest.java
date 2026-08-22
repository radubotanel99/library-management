package com.library.management.backend.member.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Create/update payload for a member ({@code API_CONTRACT.md} §7).
 *
 * <p>There is deliberately no {@code deleted} field: archiving is
 * {@code DELETE /api/members/{id}} and nothing else, so an edit can never silently
 * un-archive a row (see {@code DATA_MODEL.md} §4.1).
 *
 * @param name        display name, unique among active members, case-insensitively
 * @param email       optional address; only checked for shape, never for delivery
 * @param address     optional postal address
 * @param phoneNumber optional, kept as free text -- {@code +40} prefixes and leading
 *                    zeros are meaningful, so it is never stored as a number
 */
public record MemberRequest(
        @NotBlank @Size(max = 150) String name,
        @Email @Size(max = 255) String email,
        @Size(max = 500) String address,
        @Size(max = 50) String phoneNumber) {
}
