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

import ma.zakaria.tadbirbudget.entity.Nomenclature;
import ma.zakaria.tadbirbudget.entity.enums.NomenclatureStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A nomenclature with a summary of the definition it conforms to (so the client can label each
 * rubrique's level without a second call).
 */
public record NomenclatureResponse(
        UUID                                      id,
        String                                    name,
        String                                    description,
        NomenclatureStatus                        status,
        int                                       version,
        UUID                                      lineageId,
        UUID                                      previousVersionId,
        UUID                                      nomenclatureDefinitionId,
        String                                    definitionName,
        int                                       depth,
        List<NomenclatureDefinitionLevelResponse> levels,
        long                                      rubriqueCount,
        Instant                                   fixedAt,
        String                                    fixedBy
) {
    public static NomenclatureResponse from(Nomenclature n, String definitionName,
                                            List<NomenclatureDefinitionLevelResponse> levels,
                                            long rubriqueCount) {
        return new NomenclatureResponse(
                n.getId(), n.getName(), n.getDescription(), n.getStatus(),
                n.getVersion(), n.getLineageId(), n.getPreviousVersionId(),
                n.getNomenclatureDefinitionId(), definitionName, n.getDefinitionDepth(),
                levels, rubriqueCount, n.getFixedAt(), n.getFixedBy());
    }
}
