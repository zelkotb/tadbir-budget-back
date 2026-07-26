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

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Create a phase. The phase starts {@code CREATED}. {@code startDate}/{@code endDate} are also
 * captured as the immutable baseline (firstStartDate/firstEndDate) for delay calculation.
 */
@Data
public class CreatePhaseInput {

    @NotBlank
    @Size(max = 255)
    private String title;

    @Size(max = 5000)
    private String description;

    @NotNull
    private LocalDate startDate;

    @NotNull
    private LocalDate endDate;

    /** Share of the project (poids), 0–100. The phases' weights must sum to at most 100. */
    @NotNull
    @DecimalMin("0")
    @DecimalMax("100")
    private BigDecimal weight;

    /** Progress of this phase (avancement), 0–100. Optional; defaults to 0. */
    @DecimalMin("0")
    @DecimalMax("100")
    private BigDecimal completion;
}
