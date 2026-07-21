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

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * PATCH payload — null means "leave unchanged". When {@code levels} is provided it <b>replaces</b>
 * the whole ordered set (the list must be non-empty; names unique within the definition).
 */
@Data
public class UpdateNomenclatureDefinitionInput {

    @Size(min = 1, max = 255)
    private String name;

    @Size(max = 500)
    private String description;

    private Boolean active;

    /** When present, replaces all levels (top-down order; last = leaf). Must be non-empty. */
    private List<@NotBlank @Size(max = 100) String> levels;
}
