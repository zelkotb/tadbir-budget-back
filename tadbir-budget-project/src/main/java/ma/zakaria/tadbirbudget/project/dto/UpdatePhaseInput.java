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
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Partial update of a phase's content (only non-null fields are applied). Status is changed via the
 * dedicated status endpoint; the baseline dates (firstStartDate/firstEndDate) can never change.
 */
@Data
public class UpdatePhaseInput {

    @Size(max = 255)
    private String title;

    @Size(max = 5000)
    private String description;

    private LocalDate startDate;

    private LocalDate endDate;

    @DecimalMin("0")
    @DecimalMax("100")
    private BigDecimal weight;

    @DecimalMin("0")
    @DecimalMax("100")
    private BigDecimal completion;
}
