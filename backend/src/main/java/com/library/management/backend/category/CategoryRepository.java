package com.library.management.backend.category;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Data access for {@link Category}.
 *
 * <p>Every method filters on {@code deleted = false}: soft-deleted rows exist only
 * to keep old loan history resolvable and must never surface through the API. The
 * {@code IgnoreCase} checks mirror the {@code ux_category_name_active} partial
 * unique index, which is on {@code lower(name)}.
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findAllByDeletedFalseOrderByNameAsc();

    Optional<Category> findByIdAndDeletedFalse(Long id);

    boolean existsByIdAndDeletedFalse(Long id);

    boolean existsByNameIgnoreCaseAndDeletedFalse(String name);

    /** Duplicate check for updates: the category being edited is not its own duplicate. */
    boolean existsByNameIgnoreCaseAndDeletedFalseAndIdNot(String name, Long id);
}
