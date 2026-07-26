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
package ma.zakaria.tadbirbudget.project.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import ma.zakaria.tadbirbudget.entity.enums.PhaseStatus;

/** Change a phase's status. Transitions are forward-only: CREATED → ACTIVE → TERMINATED. */
@Data
public class UpdatePhaseStatusInput {

    @NotNull
    private PhaseStatus status;
}
