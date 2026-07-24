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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/** Create a nomenclature (the real tree) conforming to a definition. Starts in DRAFT. */
@Data
public class CreateNomenclatureInput {

    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 500)
    private String description;

    /** The definition (level template) this nomenclature must conform to. */
    @NotNull
    private UUID nomenclatureDefinitionId;
}
