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
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateNomenclatureDefinitionInput {

    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 500)
    private String description;

    /**
     * Ordered level names, from the top down; the last one is the leaf ("ligne budgétaire").
     * e.g. ["Chapitre", "Article", "Paragraphe", "Ligne"]. At least one; names unique within the
     * definition; each ≤ 100 chars.
     */
    @NotEmpty
    private List<@NotBlank @Size(max = 100) String> levels;
}
