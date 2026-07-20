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

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * PATCH payload — null means "leave unchanged".
 *
 * <p>Moving a node: set {@code parentId} to the new parent, or {@code moveToRoot=true} to detach
 * it to the top level (the two are mutually exclusive). The whole subtree follows.
 * {@code clearManager=true} removes the manager (null {@code managerId} alone means unchanged).
 */
@Data
public class UpdateOrgUnitInput {

    @Size(min = 1, max = 255)
    private String name;

    @Size(min = 1, max = 30)
    private String kind;

    /** New parent id (moves the node + its subtree). */
    private UUID parentId;

    /** Move the node to the top level (parent = null). */
    private boolean moveToRoot;

    /** New manager (users.id). */
    private UUID managerId;

    /** Remove the current manager. */
    private boolean clearManager;

    private Boolean active;
}
