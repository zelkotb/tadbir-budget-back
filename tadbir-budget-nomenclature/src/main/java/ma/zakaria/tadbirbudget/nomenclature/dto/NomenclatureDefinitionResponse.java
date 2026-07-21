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

import ma.zakaria.tadbirbudget.entity.NomenclatureDefinition;
import ma.zakaria.tadbirbudget.entity.NomenclatureDefinitionLevel;

import java.util.List;
import java.util.UUID;

public record NomenclatureDefinitionResponse(
        UUID                                     id,
        String                                   name,
        String                                   description,
        boolean                                  active,
        int                                      depth,
        List<NomenclatureDefinitionLevelResponse> levels
) {
    /** {@code levels} must be ordered by position ascending. */
    public static NomenclatureDefinitionResponse from(NomenclatureDefinition definition,
                                                      List<NomenclatureDefinitionLevel> levels) {
        int last = levels.isEmpty() ? -1 : levels.get(levels.size() - 1).getPosition();
        List<NomenclatureDefinitionLevelResponse> levelDtos = levels.stream()
                .map(l -> NomenclatureDefinitionLevelResponse.from(l, l.getPosition() == last))
                .toList();
        return new NomenclatureDefinitionResponse(
                definition.getId(), definition.getName(), definition.getDescription(),
                definition.isActive(), levels.size(), levelDtos);
    }
}
