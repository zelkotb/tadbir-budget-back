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
 * A <b>nomenclature definition</b>: the template a company's budget classification follows — an
 * ordered set of {@link NomenclatureDefinitionLevel}s (e.g. Chapitre → Article → Paragraphe →
 * Ligne, or just Chapitre → Ligne). It is naming/structure only: it says <i>how many levels there
 * are and what they are called</i>, but holds no real accounts and no money.
 *
 * <p>Not to be confused with a <i>nomenclature</i> (a later concern): the real, filled-in tree
 * built from a definition — e.g. Fonctionnement → Marina → Achat progiciel — and the amounts on
 * its leaves. A definition is low-churn reference data and is not Envers-audited.
 */
@Entity
@Table(name = "nomenclature_definition")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NomenclatureDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
