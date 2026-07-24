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

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/** PATCH project metadata — null means "leave unchanged". Org unit and status are not changed here. */
@Data
public class UpdateProjectInput {

    @Size(min = 1, max = 255)
    private String name;

    @Size(max = 2000)
    private String objectifs;

    @Size(max = 5000)
    private String description;

    private UUID chefProjetId;
}
