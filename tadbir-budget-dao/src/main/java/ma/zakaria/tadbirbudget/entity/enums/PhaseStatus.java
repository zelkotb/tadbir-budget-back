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
 * Lifecycle of a project phase. The declaration order is significant: transitions are forward-only,
 * one step at a time ({@code CREATED → ACTIVE → TERMINATED}). {@code TERMINATED} is terminal — a
 * terminated phase can neither change status nor be edited.
 */
public enum PhaseStatus {
    CREATED,
    ACTIVE,
    TERMINATED
}
