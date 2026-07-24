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

import java.util.UUID;

/**
 * One node ("rubrique") of a {@link Nomenclature} tree — e.g. the Chapitre "Fonctionnement", the
 * Article "Marina", or the leaf Ligne "Achat progiciel". Referenced by ids (not JPA associations,
 * to match the project's persistence style).
 *
 * <ul>
 *   <li>{@code levelPosition} — the rubrique's depth, matching a level of the nomenclature's
 *       definition (1 = the top level; {@code definitionDepth} = the leaf level).</li>
 *   <li>{@code leaf} — true when {@code levelPosition == definitionDepth}: the "ligne budgétaire"
 *       that will carry the amounts. Leaves cannot have children.</li>
 *   <li>{@code code} — the imputation code (e.g. "6011"); unique among <b>siblings</b> (same
 *       parent), case-insensitive, but may repeat in other branches. Enforced by two partial
 *       unique indexes (see the Liquibase change set) plus a service-level check.</li>
 * </ul>
 */
@Entity
@Table(name = "nomenclature_rubrique")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NomenclatureRubrique {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "nomenclature_id", nullable = false, columnDefinition = "uuid")
    private UUID nomenclatureId;

    /** Parent rubrique, or null for a top-level node. */
    @Column(name = "parent_id", columnDefinition = "uuid")
    private UUID parentId;

    /** 1-based depth in the tree, matching a level of the definition (leaf when == definitionDepth). */
    @Column(name = "level_position", nullable = false)
    private int levelPosition;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private boolean leaf;
}
