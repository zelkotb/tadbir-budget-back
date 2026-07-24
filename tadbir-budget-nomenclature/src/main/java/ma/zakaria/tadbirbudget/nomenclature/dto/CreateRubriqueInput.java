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

import java.util.UUID;

/**
 * Add a rubrique to a nomenclature tree. {@code parentId} null = a top-level node (level 1);
 * otherwise the new node is a child of the parent (its level = parent level + 1). The level and
 * leaf flag are derived server-side from the definition — never sent by the client.
 */
@Data
public class CreateRubriqueInput {

    /** Parent rubrique id, or null for a top-level node. */
    private UUID parentId;

    /** Imputation code, unique within the nomenclature (e.g. "60", "6011"). */
    @NotBlank
    @Size(max = 50)
    private String code;

    @NotBlank
    @Size(max = 255)
    private String label;
}
