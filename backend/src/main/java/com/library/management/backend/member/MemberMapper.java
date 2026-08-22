package com.library.management.backend.member;

import com.library.management.backend.member.dto.MemberResponse;

/**
 * Entity to DTO conversion for members.
 *
 * <p>Package-private and static: the mapping is a pure function with no state and
 * no dependencies, so making it a Spring bean would buy nothing. Keeping it out of
 * the {@code dto} package and out of public view means only the service can call
 * it -- the entity never gets a chance to leave the service layer.
 *
 * <p>{@code openLoanCount} is passed in rather than read off the entity:
 * {@code Member} intentionally has no {@code @OneToMany} to loans, so counting is
 * always a deliberate query the service batches, never a lazy load per row.
 */
final class MemberMapper {

    private MemberMapper() {
    }

    static MemberResponse toResponse(Member member, long openLoanCount) {
        return new MemberResponse(
                member.getId(),
                member.getName(),
                member.getEmail(),
                member.getAddress(),
                member.getPhoneNumber(),
                openLoanCount,
                member.getCreatedAt());
    }
}
