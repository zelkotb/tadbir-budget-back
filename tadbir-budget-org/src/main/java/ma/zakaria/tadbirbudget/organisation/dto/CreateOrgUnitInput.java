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
package ma.zakaria.tadbirbudget.organisation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

@Data
public class CreateOrgUnitInput {

    @NotBlank
    @Size(max = 255)
    private String name;

    /** Free label: POLE, DIRECTION, DEPARTEMENT, SERVICE, … Any value; not a structural rule. */
    @NotBlank
    @Size(max = 30)
    private String kind;

    /** Parent unit id, or null to create a root. Any kind may parent any kind. */
    private UUID parentId;

    /** Optional manager (users.id). */
    private UUID managerId;
}
