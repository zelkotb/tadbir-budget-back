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

/**
 * PATCH payload for a nomenclature's metadata — null means "leave unchanged". The definition and
 * the status are not changed here (status has dedicated fix/archive endpoints).
 */
@Data
public class UpdateNomenclatureInput {

    @Size(min = 1, max = 255)
    private String name;

    @Size(max = 500)
    private String description;
}
