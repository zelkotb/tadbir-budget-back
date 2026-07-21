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

import java.util.UUID;

/**
 * One ordered level of a {@link NomenclatureDefinition} (e.g. position 1 = "Chapitre", … , the
 * last position = "Ligne", the leaf). Referenced by {@code nomenclature_definition_id} (id, not a
 * JPA association, to match the project's persistence style). The deepest {@code position} is the
 * leaf level.
 */
@Entity
@Table(name = "nomenclature_definition_level",
        uniqueConstraints = @UniqueConstraint(name = "uk_nomenclature_definition_level_position",
                columnNames = {"nomenclature_definition_id", "position"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NomenclatureDefinitionLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "nomenclature_definition_id", nullable = false, columnDefinition = "uuid")
    private UUID nomenclatureDefinitionId;

    /** 1-based rank of the level within its definition; the highest position is the leaf. */
    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 100)
    private String name;
}
