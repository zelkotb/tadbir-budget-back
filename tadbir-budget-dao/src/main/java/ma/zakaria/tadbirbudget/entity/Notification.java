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
package ma.zakaria.tadbirbudget.entity;

import jakarta.persistence.*;
import lombok.*;
import ma.zakaria.tadbirbudget.entity.enums.NotificationChannel;
import ma.zakaria.tadbirbudget.entity.enums.NotificationStatus;
import org.hibernate.envers.Audited;

import java.time.Instant;
import java.util.UUID;

/**
 * One queued message for one recipient on one channel. This is the durable queue:
 * a notification is born {@code PENDING}, a background dispatcher picks it up and
 * tries to deliver it (respecting per-channel rate limits and back-off), then marks
 * it {@code SENT} or — after exhausting retries — {@code FAILED}.
 *
 * <p>The recipient's e-mail is <em>snapshotted</em> at enqueue time so the audit trail
 * shows exactly where the message was sent, even if the user later changes address.
 *
 * <p>{@code @Audited} (Hibernate Envers) records every change into a {@code notification_audit}
 * table — including each retry — giving a full delivery history.
 */
@Audited
@Entity
@Table(
    name = "notification",
    indexes = {
        @Index(name = "idx_notification_recipient", columnList = "recipient_id"),
        // drives the dispatcher scan: "what is due to send, per channel"
        @Index(name = "idx_notification_dispatch",  columnList = "channel, status, next_attempt_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    /** The user this message is for. No FK — notifications outlive user deletion. */
    @Column(name = "recipient_id", nullable = false, columnDefinition = "uuid")
    private UUID recipientId;

    /** Snapshot of the recipient address used for MAIL (null for APPLICATION-only). */
    @Column(name = "recipient_email", length = 255)
    private String recipientEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationChannel channel;

    /**
     * Free-form grouping tag, e.g. "WORKFLOW_STEP_ASSIGNED" — lets the frontend pick an
     * icon and lets us query "all workflow notifications". Never parsed by the engine.
     */
    @Column(length = 100)
    private String category;

    /** Opaque pointer back to the originating record (e.g. a workflow businessKey). */
    @Column(name = "reference_key", length = 255)
    private String referenceKey;

    @Column(nullable = false, length = 255)
    private String subject;

    @Column(nullable = false, length = 4000)
    private String body;

    /** True when {@code body} is HTML (rendered MAIL templates); false for plain text / in-app. */
    @Column(nullable = false)
    @Builder.Default
    private boolean html = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private NotificationStatus status = NotificationStatus.PENDING;

    /** How many delivery attempts have been made so far. */
    @Column(nullable = false)
    @Builder.Default
    private int attempts = 0;

    /** Earliest time the dispatcher may (re)try this one — moved forward on each failure (back-off). */
    @Column(name = "next_attempt_at", nullable = false)
    @Builder.Default
    private Instant nextAttemptAt = Instant.now();

    /** Last delivery error, for diagnostics (null while healthy). */
    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /** When delivery succeeded. */
    @Column(name = "sent_at")
    private Instant sentAt;

    /** When the user opened it (APPLICATION channel only). */
    @Column(name = "read_at")
    private Instant readAt;
}
