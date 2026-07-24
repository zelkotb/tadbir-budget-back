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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ma.zakaria.tadbirbudget.entity.enums.NomenclatureStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * A <b>nomenclature</b>: the real, filled-in budget tree built from a
 * {@link NomenclatureDefinition} — e.g. Fonctionnement → Marina → Achat progiciel. Its nodes are
 * {@link NomenclatureRubrique}s; the leaves (deepest level of the definition) are the "lignes
 * budgétaires" that will eventually carry the amounts.
 *
 * <p>Lifecycle (see {@link NomenclatureStatus}): built as {@code DRAFT}, then {@code FIXED}
 * (locked) so real budgets can be built on a stable structure. {@code definitionDepth} is captured
 * from the definition at creation so the tree's shape stays valid even if the definition template
 * is later edited.
 */
@Entity
@Table(name = "nomenclature")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Nomenclature {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    /** Shared across all versions of the same lineage — not globally unique (see {@code lineageId}). */
    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    /** 1 for the first version; incremented on each clone within the lineage. */
    @Column(nullable = false)
    private int version;

    /** Groups all versions of the same nomenclature (stable across clones). */
    @Column(name = "lineage_id", nullable = false, columnDefinition = "uuid")
    private UUID lineageId;

    /** The version this one was cloned from, or null for the first version. */
    @Column(name = "previous_version_id", columnDefinition = "uuid")
    private UUID previousVersionId;

    /** The definition (level template) this nomenclature conforms to. */
    @Column(name = "nomenclature_definition_id", nullable = false, columnDefinition = "uuid")
    private UUID nomenclatureDefinitionId;

    /** Number of levels of the definition, captured at creation (the max rubrique depth). */
    @Column(name = "definition_depth", nullable = false)
    private int definitionDepth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NomenclatureStatus status;

    @Column(name = "fixed_at")
    private Instant fixedAt;

    /** uid of who fixed the nomenclature. */
    @Column(name = "fixed_by", length = 255)
    private String fixedBy;
}
