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

/**
 * Derived schedule health, from the gap between actual and planned advancement (±5 points tolerance).
 * A ready-to-use badge for the front — the raw figures (écart, SPI, retard) are also returned so the
 * client can apply its own rule if preferred.
 */
public enum ScheduleHealth {
    EN_AVANCE,        // ahead of plan
    DANS_LES_TEMPS,   // on plan (within tolerance)
    EN_RETARD,        // behind plan
    INDETERMINE       // nothing planned yet to compare against
}
