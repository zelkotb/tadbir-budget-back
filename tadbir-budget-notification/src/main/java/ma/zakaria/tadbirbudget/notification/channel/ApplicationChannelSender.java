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

import lombok.extern.slf4j.Slf4j;
import ma.zakaria.tadbirbudget.entity.Notification;
import ma.zakaria.tadbirbudget.entity.enums.NotificationChannel;
import org.springframework.stereotype.Component;

/**
 * "Delivers" an in-app notification. The message already lives in the database, so
 * delivery just means marking it SENT (done by the dispatcher) — the frontend then
 * reads it from the notification bell. Kept as a sender so it flows through the same
 * rate-limited, retryable pipeline as e-mail.
 */
@Slf4j
@Component
public class ApplicationChannelSender implements NotificationChannelSender {

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.APPLICATION;
    }

    @Override
    public void send(Notification notification) {
        // No external system to call — the row itself is the in-app message.
        log.debug("In-app notification delivered recipient={} subject=\"{}\"",
                notification.getRecipientId(), notification.getSubject());
    }
}
