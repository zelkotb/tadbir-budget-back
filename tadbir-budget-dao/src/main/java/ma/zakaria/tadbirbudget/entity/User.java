/*
 * Copyright (c) 2026 Zakaria El Kotb. All rights reserved.
 *
 * This source code is the exclusive property of Zakaria El Kotb.
 * Unauthorized copying, modification, distribution, or use of this file,
 * via any medium, is strictly prohibited without the prior written
 * permission of the copyright owner.
 *
 * Author: Zakaria El Kotb <elkotbzakaria@gmail.com>
 */
package ma.zakaria.tadbirbudget.entity;

import jakarta.persistence.*;
import lombok.*;
import ma.zakaria.tadbirbudget.converter.StringListConverter;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.*;

@Audited
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    /** Login identifier (e.g. {@code p.admin}). Unique — this is what the user authenticates with. */
    @Column(nullable = false, unique = true)
    private String uid;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String phoneNumber;

    @Column(nullable = false, unique = true)
    private String email;

    /**
     * This user's direct superior in the validation hierarchy (their "N+1"), or null at
     * the top of the chain. The superior's title is irrelevant — for one user the person
     * one level up is a manager, for another a department chef. The workflow
     * {@code HIERARCHY} approver type climbs this link N levels to find an approver.
     */
    @Column(name = "manager_id", columnDefinition = "uuid")
    private UUID managerId;

    /** The organisation unit (pôle / direction / département …) this user belongs to, optional. */
    @Column(name = "org_unit_id", columnDefinition = "uuid")
    private UUID orgUnitId;

    @NotAudited
    @Column(nullable = false)
    private String password;

    /**
     * Comma-separated role string, e.g. "ROLE_ADMIN,ROLE_INSTRUCTOR".
     * Naming convention: role names must never be a prefix or substring of
     * another role — see {@link ma.zakaria.tadbirbudget.constant.Roles}.
     */
    @Convert(converter = StringListConverter.class)
    @Column(name = "roles", nullable = false)
    @Builder.Default
    private List<String> roles = new ArrayList<>();

    @Column(nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @NotAudited
    @Column(name = "failed_login_attempts", nullable = false)
    @Builder.Default
    private int failedLoginAttempts = 0;

    // ── UserDetails ──────────────────────────────────────────────────────────

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    @Override
    public String getUsername() {
        return uid;
    }

    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return enabled; }
}