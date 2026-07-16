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
package ma.zakaria.tadbirbudget.notification.dto;

import ma.zakaria.tadbirbudget.entity.Notification;
import ma.zakaria.tadbirbudget.entity.enums.NotificationStatus;

import java.time.Instant;
import java.util.UUID;

/** What the frontend notification bell renders for one in-app message. */
public record NotificationView(
        UUID id,
        String category,
        String referenceKey,
        String subject,
        String body,
        boolean read,
        Instant createdAt,
        Instant readAt) {

    public static NotificationView from(Notification n) {
        return new NotificationView(
                n.getId(),
                n.getCategory(),
                n.getReferenceKey(),
                n.getSubject(),
                n.getBody(),
                n.getStatus() == NotificationStatus.READ,
                n.getCreatedAt(),
                n.getReadAt());
    }
}
