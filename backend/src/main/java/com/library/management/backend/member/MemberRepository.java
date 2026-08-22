package com.library.management.backend.member;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Data access for {@link Member}.
 *
 * <p>Every method filters on {@code deleted = false}: soft-deleted rows exist only
 * to keep old loan history resolvable and must never surface through the API. The
 * {@code IgnoreCase} checks mirror the {@code ux_member_name_active} partial unique
 * index, which is on {@code lower(name)}.
 */
public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByIdAndDeletedFalse(Long id);

    boolean existsByNameIgnoreCaseAndDeletedFalse(String name);

    /** Duplicate check for updates: the member being edited is not its own duplicate. */
    boolean existsByNameIgnoreCaseAndDeletedFalseAndIdNot(String name, Long id);

    /**
     * One page of active members, searched ({@code API_CONTRACT.md} §7).
     *
     * <p>{@code pattern} arrives lowercased and wrapped in {@code %}, or {@code null}
     * when there is no search term, which the {@code :pattern is null or ...} guard
     * turns into "no filter". The phone number is matched as plain text, exactly as
     * it was typed in: the column is a free-form string ({@code +40 721 ...},
     * leading zeros), and normalising digits here would need the same normalisation
     * applied at write time to be of any use.
     *
     * <p>The count query is written out by hand rather than derived, matching
     * {@code BookRepository.search}: Spring Data derives counts from the query text,
     * so keeping the two side by side makes the pair obvious to whoever edits either.
     */
    @Query(value = """
            select m from Member m
            where m.deleted = false
              and (:pattern is null
                   or lower(m.name) like :pattern
                   or lower(m.email) like :pattern
                   or lower(m.phoneNumber) like :pattern)
            """,
            countQuery = """
            select count(m) from Member m
            where m.deleted = false
              and (:pattern is null
                   or lower(m.name) like :pattern
                   or lower(m.email) like :pattern
                   or lower(m.phoneNumber) like :pattern)
            """)
    Page<Member> search(@Param("pattern") String pattern, Pageable pageable);
}
