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

import lombok.Data;

import java.time.LocalDate;

/**
 * Start a project (NOT_STARTED → ACTIVE). The whole body is optional: when {@code startDate} is
 * omitted the server records today's date. Supplying it lets the responsible back-date a start
 * that actually happened earlier.
 */
@Data
public class StartProjectInput {

    /** Optional; defaults to today when null. */
    private LocalDate startDate;
}
