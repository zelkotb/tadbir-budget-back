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
package ma.zakaria.tadbirbudget.entity.enums;

/**
 * Lifecycle of a project.
 * <ul>
 *   <li>{@code NOT_STARTED} — created but not begun (the default); the responsible starts it.</li>
 *   <li>{@code ACTIVE}      — started (carries the {@code startDate}); can be budgeted and edited.</li>
 *   <li>{@code TERMINATED}  — finished (carries the {@code terminationYear}).</li>
 *   <li>{@code ARCHIVED}    — retired; read-only.</li>
 * </ul>
 */
public enum ProjectStatus {
    NOT_STARTED,
    ACTIVE,
    TERMINATED,
    ARCHIVED
}
