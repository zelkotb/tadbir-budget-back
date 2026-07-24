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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Assigns a {@link NomenclatureRubrique} (at any level) to an {@link OrgUnit}. Many-to-many: the
 * same rubrique may be assigned to several org units. Assigning a node implicitly grants its whole
 * rubrique-subtree; the grant is usable by that org unit <b>and every org unit below it</b>.
 *
 * <p>{@code nomenclatureId} is denormalized (the rubrique always belongs to one nomenclature) so
 * assignments can be listed / resolved per nomenclature without a join.
 */
@Entity
@Table(name = "rubrique_assignment",
        uniqueConstraints = @UniqueConstraint(name = "uk_rubrique_assignment",
                columnNames = {"rubrique_id", "org_unit_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RubriqueAssignment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "rubrique_id", nullable = false, columnDefinition = "uuid")
    private UUID rubriqueId;

    @Column(name = "nomenclature_id", nullable = false, columnDefinition = "uuid")
    private UUID nomenclatureId;

    @Column(name = "org_unit_id", nullable = false, columnDefinition = "uuid")
    private UUID orgUnitId;

    /** uid of who created the assignment. */
    @Column(name = "assigned_by", length = 255)
    private String assignedBy;

    @Column(name = "assigned_at")
    private Instant assignedAt;
}
