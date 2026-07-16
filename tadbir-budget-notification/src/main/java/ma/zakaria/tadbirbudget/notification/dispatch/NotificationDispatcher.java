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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.zakaria.tadbirbudget.entity.Notification;
import ma.zakaria.tadbirbudget.entity.enums.NotificationChannel;
import ma.zakaria.tadbirbudget.entity.enums.NotificationStatus;
import ma.zakaria.tadbirbudget.notification.config.NotificationProperties;
import ma.zakaria.tadbirbudget.repository.NotificationRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Background worker that drains the notification queue. On each tick (default every
 * second, {@code notification.dispatch.fixed-delay-ms}) it pulls the due {@code PENDING}
 * rows per channel and delivers them one by one, taking a rate-limiter token first.
 * When the per-second budget is spent it stops and leaves the rest for the next tick —
 * that is how the 2 mails/s and 10 in-app/s caps are enforced.
 *
 * <p>The queue is the database, so unsent notifications survive a restart and the audit
 * trail captures every attempt.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationRepository       repository;
    private final NotificationRateLimiter      rateLimiter;
    private final NotificationDeliveryService  deliveryService;
    private final NotificationProperties       properties;

    @Scheduled(fixedDelayString = "${notification.dispatch.fixed-delay-ms:5000}")
    @SchedulerLock(name = "notificationDispatcher", lockAtMostFor = "PT1M", lockAtLeastFor = "PT0S")
    public void tick() {
        if (!properties.isEnabled()) {
            return;
        }
        for (NotificationChannel channel : NotificationChannel.values()) {
            drain(channel);
        }
    }

    private void drain(NotificationChannel channel) {
        List<Notification> due = repository
                .findByChannelAndStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                        channel, NotificationStatus.PENDING, Instant.now(),
                        PageRequest.of(0, properties.getDispatch().getBatchSize()));
        if (due.isEmpty()) {
            return;
        }
        int sent = 0;
        for (Notification n : due) {
            if (!rateLimiter.tryAcquire(channel)) {
                break; // per-second budget spent — the rest wait for the next tick
            }
            deliveryService.deliver(n.getId());
            sent++;
        }
        log.debug("Dispatcher channel={} dueFetched={} processed={}", channel, due.size(), sent);
    }
}
