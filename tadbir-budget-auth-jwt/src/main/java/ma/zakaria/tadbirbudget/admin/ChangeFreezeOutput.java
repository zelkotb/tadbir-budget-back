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
package ma.zakaria.tadbirbudget.admin;

import java.time.Instant;
import java.util.UUID;

/** Current state of the global change-freeze switch. */
public record ChangeFreezeOutput(boolean frozen, Instant updatedAt, UUID updatedBy) {
}
