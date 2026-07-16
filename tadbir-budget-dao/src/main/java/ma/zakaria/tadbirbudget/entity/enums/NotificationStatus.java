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
 * Lifecycle of a single queued notification.
 *
 * <pre>
 *   PENDING ──(dispatcher sends it)──▶ SENT ──(user opens it, APPLICATION only)──▶ READ
 *      │
 *      └──(send fails, retries exhausted)──▶ FAILED
 * </pre>
 *
 * While {@code PENDING}, the dispatcher keeps retrying with a growing back-off delay
 * (see {@code next_attempt_at}). After the configured maximum number of attempts a
 * notification is marked {@code FAILED} and is no longer retried.
 */
public enum NotificationStatus {
    PENDING,
    SENT,
    READ,
    FAILED
}
