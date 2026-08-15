package com.library.management.backend.member;

import com.library.management.backend.common.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * A library member who can borrow books.
 *
 * <p>Members are soft-deleted via {@link #deleted}; the partial unique index
 * {@code ux_member_name_active} keeps names unique among live rows only.
 */
@Entity
@Table(name = "member")
@Getter
@Setter
public class Member extends AuditableEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 255)
    private String email;

    @Column(length = 500)
    private String address;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    @Column(nullable = false)
    private boolean deleted = false;
}
