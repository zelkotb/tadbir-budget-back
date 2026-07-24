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

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** Create a project. Starts ACTIVE, owned by {@code orgUnitId} (in the creator's subtree). */
@Data
public class CreateProjectInput {

    @NotBlank
    @Size(max = 255)
    private String name;

    @Size(max = 2000)
    private String objectifs;

    @Size(max = 5000)
    private String description;

    /** Chef de projet — a user id. */
    @NotNull
    private UUID chefProjetId;

    /** The owning org unit (must be the creator's own unit or a descendant). */
    @NotNull
    private UUID orgUnitId;

    @Valid
    private List<ProjectMemberInput> team;
}
