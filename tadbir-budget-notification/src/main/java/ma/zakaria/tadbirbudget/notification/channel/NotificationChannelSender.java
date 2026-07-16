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
package ma.zakaria.tadbirbudget.notification.channel;

import ma.zakaria.tadbirbudget.entity.Notification;
import ma.zakaria.tadbirbudget.entity.enums.NotificationChannel;

/**
 * Strategy for delivering one notification on one channel. The dispatcher picks the
 * sender whose {@link #channel()} matches. Add a new channel (e.g. SMS, push) by
 * adding one bean — the dispatcher never changes.
 */
public interface NotificationChannelSender {

    /** Which channel this sender handles. */
    NotificationChannel channel();

    /**
     * Deliver the message. Must throw on failure so the dispatcher can record the
     * error and schedule a back-off retry.
     */
    void send(Notification notification) throws Exception;
}
