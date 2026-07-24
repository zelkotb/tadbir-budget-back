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

import jakarta.validation.constraints.Size;
import lombok.Data;

/** PATCH a rubrique's code/label — null means "leave unchanged". Re-parenting is not supported. */
@Data
public class UpdateRubriqueInput {

    @Size(min = 1, max = 50)
    private String code;

    @Size(min = 1, max = 255)
    private String label;
}
