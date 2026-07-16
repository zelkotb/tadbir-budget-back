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
package ma.zakaria.tadbirbudget.notification.api;

import ma.zakaria.tadbirbudget.entity.enums.NotificationChannel;
import ma.zakaria.tadbirbudget.notification.template.NotificationTemplate;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What any module passes to {@link NotificationService#enqueue} to send a message.
 * One request fans out to one queued row per requested {@code channel}.
 *
 * <p>The subject and body are not supplied directly — they come from a
 * {@link NotificationTemplate} rendered with {@code model}. This keeps wording and
 * branding in one place and out of business code.
 *
 * @param recipientId    the user to notify
 * @param recipientEmail the user's e-mail (required for MAIL; may be null otherwise)
 * @param channels       which channels to send on (MAIL, APPLICATION, or both)
 * @param template       which template to render for subject + body
 * @param model          values filled into the template (e.g. businessKey, stepName)
 * @param category       grouping tag for the frontend (defaults to the template name)
 * @param referenceKey   opaque pointer back to the source record (e.g. workflow businessKey)
 */
public record NotificationRequest(
        UUID recipientId,
        String recipientEmail,
        Set<NotificationChannel> channels,
        NotificationTemplate template,
        Map<String, Object> model,
        String category,
        String referenceKey) {

    /** Convenience: category defaults to the template's name. */
    public static NotificationRequest of(UUID recipientId, String recipientEmail,
                                         Set<NotificationChannel> channels,
                                         NotificationTemplate template, Map<String, Object> model,
                                         String referenceKey) {
        return new NotificationRequest(recipientId, recipientEmail, channels, template, model,
                template.name(), referenceKey);
    }
}
