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
package ma.zakaria.tadbirbudget.nomenclature.dto;

import ma.zakaria.tadbirbudget.entity.NomenclatureRubrique;

import java.util.UUID;

/**
 * One node of a nomenclature tree. {@code levelName} is resolved from the definition (e.g.
 * "Chapitre" for level 1); {@code leaf} marks the "ligne budgétaire" that will carry amounts.
 * The client builds the tree from {@code parentId}.
 */
public record RubriqueResponse(
        UUID    id,
        UUID    nomenclatureId,
        UUID    parentId,
        int     levelPosition,
        String  levelName,
        String  code,
        String  label,
        boolean leaf
) {
    public static RubriqueResponse from(NomenclatureRubrique r, String levelName) {
        return new RubriqueResponse(
                r.getId(), r.getNomenclatureId(), r.getParentId(), r.getLevelPosition(),
                levelName, r.getCode(), r.getLabel(), r.isLeaf());
    }
}
