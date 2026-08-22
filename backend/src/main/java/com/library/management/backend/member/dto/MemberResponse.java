package com.library.management.backend.member.dto;

import java.time.Instant;

/**
 * What the API returns for a member ({@code API_CONTRACT.md} §7).
 *
 * <p>No {@code updatedAt}: the contract lists these seven fields exactly, and the
 * member screens never show it -- the same choice {@code CategoryResponse} makes.
 *
 * @param id            surrogate key
 * @param name          display name
 * @param email         optional address
 * @param address       optional postal address
 * @param phoneNumber   optional, free text
 * @param openLoanCount books this member currently holds, so the UI can show the
 *                      remaining allowance without a second call and warn before
 *                      archiving
 * @param createdAt     when the member joined, UTC
 */
public record MemberResponse(
        Long id,
        String name,
        String email,
        String address,
        String phoneNumber,
        long openLoanCount,
        Instant createdAt) {
}
