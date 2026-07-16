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
 * How a notification reaches its recipient.
 * <ul>
 *   <li>{@code MAIL} — an e-mail sent through the SMTP server (rate-limited, slower).</li>
 *   <li>{@code APPLICATION} — an in-app message shown in the user's notification bell
 *       (rate-limited, faster — it is just a database row the frontend reads).</li>
 * </ul>
 * A single business event (e.g. "a request awaits your validation") can be sent on
 * both channels — that produces two rows, one per channel.
 */
public enum NotificationChannel {
    MAIL,
    APPLICATION
}
