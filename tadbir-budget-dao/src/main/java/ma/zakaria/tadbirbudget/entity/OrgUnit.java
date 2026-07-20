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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;

import java.util.UUID;

/**
 * A node of the organisation structure (pôle / direction / département / service …).
 *
 * <p>The tree is deliberately <b>unconstrained</b>: {@code kind} is a label, not a rule, so a
 * département may hang directly under a pôle, or under a direction — whatever the company's real
 * structure is. Nesting is a simple {@code parent_id} self-reference plus a <b>materialized path</b>
 * ({@code /rootId/childId/…/}) that makes "whole subtree" queries a single
 * {@code path LIKE 'prefix%'}.
 *
 * <p>{@code path} and {@code depth} are derived bookkeeping maintained by the org service
 * (including bulk rewrites when a node is moved) — they are {@code @NotAudited}; the meaningful
 * business change (the {@code parentId}) is what Envers records.
 */
@Audited
@Entity
@Table(name = "org_unit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrgUnit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** Free label of the unit's nature: POLE, DIRECTION, DEPARTEMENT, SERVICE, … (not a constraint). */
    @Column(nullable = false, length = 30)
    private String kind;

    /** Parent unit, or null for a root. Any kind may parent any kind. */
    @Column(name = "parent_id", columnDefinition = "uuid")
    private UUID parentId;

    /** The user managing this unit (chef de pôle / directeur / chef de département), optional. */
    @Column(name = "manager_id", columnDefinition = "uuid")
    private UUID managerId;

    /** Materialized path of ids, e.g. {@code /a1/b2/c3/} — always ends with this node's id. */
    @NotAudited
    @Column(nullable = false, length = 1000)
    private String path;

    /** 0 for roots, parent.depth + 1 otherwise. */
    @NotAudited
    @Column(nullable = false)
    private int depth;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
