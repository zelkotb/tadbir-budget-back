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
package ma.zakaria.tadbirbudget.notification.template;

import lombok.Getter;

/**
 * The catalogue of notification templates. Every notification is sent through one of
 * these — no ad-hoc subject/body strings at call sites.
 *
 * <p>Each entry defines:
 * <ul>
 *   <li>{@link #subject} — a short inline Thymeleaf TEXT template (the e-mail subject /
 *       in-app title);</li>
 *   <li>{@link #appBody} — an inline Thymeleaf TEXT template for the in-app message;</li>
 *   <li>{@link #mailTemplate} — the name of the rich HTML file under
 *       {@code resources/templates/notification/mail/&lt;name&gt;.html} used for e-mail.</li>
 * </ul>
 * Placeholders use Thymeleaf inlining, e.g. {@code [(${requestNumber})]}, filled from the
 * model map passed to {@code NotificationService.enqueue}.
 */
@Getter
public enum NotificationTemplate {

    /** An approver's turn has come up on a workflow step. */
    WORKFLOW_STEP_ASSIGNED(
            "Une tâche attend votre validation",
            "Bonjour [(${recipientName})], une tâche attend votre validation "
                    + "à l'étape « [(${stepName})] ».",
            "workflow-step-assigned");

    private final String subject;
    private final String appBody;
    private final String mailTemplate;

    NotificationTemplate(String subject, String appBody, String mailTemplate) {
        this.subject = subject;
        this.appBody = appBody;
        this.mailTemplate = mailTemplate;
    }
}
