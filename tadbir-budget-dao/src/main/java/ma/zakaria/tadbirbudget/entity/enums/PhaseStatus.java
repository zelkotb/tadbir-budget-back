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
 * Lifecycle of a project phase or sous-phase. Allowed transitions:
 * {@code CREATED → ACTIVE → TERMINATED}, plus {@code CREATED → CANCELLED} and
 * {@code ACTIVE → CANCELLED}. {@code TERMINATED} and {@code CANCELLED} (résilié/annulé) are terminal —
 * a closed phase can neither change status nor be edited. Cancelled phases are excluded from the KPIs.
 */
public enum PhaseStatus {
    CREATED,
    ACTIVE,
    TERMINATED,
    CANCELLED
}
