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

import java.time.Instant;
import java.util.UUID;

/** A rubrique → org-unit assignment, with the rubrique and org-unit labels resolved for display. */
public record RubriqueAssignmentResponse(
        UUID    id,
        UUID    rubriqueId,
        String  rubriqueCode,
        String  rubriqueLabel,
        int     levelPosition,
        String  levelName,
        boolean leaf,
        UUID    orgUnitId,
        String  orgUnitName,
        String  assignedBy,
        Instant assignedAt
) {}
