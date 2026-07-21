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

import ma.zakaria.tadbirbudget.entity.NomenclatureDefinitionLevel;

import java.util.UUID;

/** One level of a nomenclature definition. {@code leaf} is true for the deepest level (the "ligne"). */
public record NomenclatureDefinitionLevelResponse(
        UUID    id,
        int     position,
        String  name,
        boolean leaf
) {
    public static NomenclatureDefinitionLevelResponse from(NomenclatureDefinitionLevel level, boolean leaf) {
        return new NomenclatureDefinitionLevelResponse(level.getId(), level.getPosition(), level.getName(), leaf);
    }
}
