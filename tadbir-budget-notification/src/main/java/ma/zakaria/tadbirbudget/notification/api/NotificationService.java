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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.zakaria.tadbirbudget.entity.Notification;
import ma.zakaria.tadbirbudget.entity.enums.NotificationChannel;
import ma.zakaria.tadbirbudget.entity.enums.NotificationStatus;
import ma.zakaria.tadbirbudget.notification.template.NotificationTemplateRenderer;
import ma.zakaria.tadbirbudget.notification.template.RenderedMessage;
import ma.zakaria.tadbirbudget.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

/**
 * The single entry point other modules use to send a notification. It only
 * <em>enqueues</em> — it persists one {@code PENDING} row per requested channel and
 * returns immediately. A background {@code NotificationDispatcher} does the actual
 * (rate-limited, retried) delivery, so callers never block on SMTP.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository        repository;
    private final NotificationTemplateRenderer  renderer;

    /**
     * Queue a notification on each requested channel. The subject/body are rendered now
     * from the request's template, so the message content is captured at enqueue time.
     * Safe to call inside the caller's transaction — the rows commit with it; nothing is
     * sent synchronously.
     */
    @Transactional
    public void enqueue(NotificationRequest request) {
        Set<NotificationChannel> channels = request.channels();
        if (request.recipientId() == null || channels == null || channels.isEmpty()
                || request.template() == null) {
            log.warn("Skipping notification enqueue with no recipient/channels/template category={}",
                    request.category());
            return;
        }
        for (NotificationChannel channel : channels) {
            if (channel == NotificationChannel.MAIL
                    && (request.recipientEmail() == null || request.recipientEmail().isBlank())) {
                log.warn("Skipping MAIL notification — no e-mail for recipient={} template={}",
                        request.recipientId(), request.template());
                continue;
            }
            RenderedMessage rendered = renderer.render(request.template(), channel, request.model());
            repository.save(Notification.builder()
                    .recipientId(request.recipientId())
                    .recipientEmail(request.recipientEmail())
                    .channel(channel)
                    .category(request.category())
                    .referenceKey(request.referenceKey())
                    .subject(rendered.subject())
                    .body(rendered.body())
                    .html(rendered.html())
                    .status(NotificationStatus.PENDING)
                    .attempts(0)
                    .nextAttemptAt(Instant.now())
                    .createdAt(Instant.now())
                    .build());
        }
        log.debug("Enqueued notification template={} recipient={} channels={}",
                request.template(), request.recipientId(), channels);
    }
}
