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

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/** Assign a rubrique (any level) to an org unit. */
@Data
public class CreateRubriqueAssignmentInput {

    @NotNull
    private UUID rubriqueId;

    @NotNull
    private UUID orgUnitId;
}
