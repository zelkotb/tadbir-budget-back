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
package ma.zakaria.tadbirbudget.notification.dispatch;

import lombok.extern.slf4j.Slf4j;
import ma.zakaria.tadbirbudget.entity.Notification;
import ma.zakaria.tadbirbudget.entity.enums.NotificationChannel;
import ma.zakaria.tadbirbudget.entity.enums.NotificationStatus;
import ma.zakaria.tadbirbudget.notification.channel.NotificationChannelSender;
import ma.zakaria.tadbirbudget.notification.config.NotificationProperties;
import ma.zakaria.tadbirbudget.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Delivers a single notification in its own transaction, applying back-off on failure.
 * Isolated from the dispatcher loop so one bad message never rolls back the others and
 * each delivery commits as soon as it finishes.
 */
@Slf4j
@Service
public class NotificationDeliveryService {

    private static final int MAX_ERROR_LEN = 1000;

    private final NotificationRepository repository;
    private final NotificationProperties properties;
    private final Map<NotificationChannel, NotificationChannelSender> senders =
            new EnumMap<>(NotificationChannel.class);

    public NotificationDeliveryService(NotificationRepository repository,
                                       NotificationProperties properties,
                                       List<NotificationChannelSender> channelSenders) {
        this.repository = repository;
        this.properties = properties;
        channelSenders.forEach(s -> this.senders.put(s.channel(), s));
    }

    /**
     * Attempt to deliver one notification. On success → SENT; on failure → either a
     * back-off retry (still PENDING, {@code next_attempt_at} pushed out) or FAILED once
     * {@code maxAttempts} is reached. Each call is a fresh transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deliver(UUID notificationId) {
        Notification n = repository.findById(notificationId).orElse(null);
        if (n == null || n.getStatus() != NotificationStatus.PENDING) {
            return; // already handled (or vanished) — nothing to do
        }
        NotificationChannelSender sender = senders.get(n.getChannel());
        if (sender == null) {
            log.error("No sender for channel={} notification={}", n.getChannel(), n.getId());
            return;
        }
        try {
            sender.send(n);
            n.setStatus(NotificationStatus.SENT);
            n.setSentAt(Instant.now());
            n.setLastError(null);
        } catch (Exception ex) {
            applyFailure(n, ex);
        }
        repository.save(n);
    }

    private void applyFailure(Notification n, Exception ex) {
        int attempts = n.getAttempts() + 1;
        n.setAttempts(attempts);
        n.setLastError(truncate(ex.getMessage()));
        NotificationProperties.Backoff backoff = properties.getBackoff();
        if (attempts >= backoff.getMaxAttempts()) {
            n.setStatus(NotificationStatus.FAILED);
            log.error("Notification {} FAILED after {} attempts channel={} recipient={}",
                    n.getId(), attempts, n.getChannel(), n.getRecipientId(), ex);
        } else {
            long delay = backoffSeconds(backoff, attempts);
            n.setNextAttemptAt(Instant.now().plusSeconds(delay));
            log.warn("Notification {} delivery failed (attempt {}/{}) — retrying in {}s: {}",
                    n.getId(), attempts, backoff.getMaxAttempts(), delay, ex.getMessage());
        }
    }

    /** initialSeconds × multiplier^(attempt-1) — exponential growth between retries. */
    private long backoffSeconds(NotificationProperties.Backoff backoff, int attempt) {
        double delay = backoff.getInitialSeconds() * Math.pow(backoff.getMultiplier(), attempt - 1.0);
        return (long) Math.min(delay, Long.MAX_VALUE);
    }

    private String truncate(String s) {
        if (s == null) return null;
        return s.length() <= MAX_ERROR_LEN ? s : s.substring(0, MAX_ERROR_LEN);
    }
}
