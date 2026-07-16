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

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import ma.zakaria.tadbirbudget.entity.enums.NotificationChannel;
import ma.zakaria.tadbirbudget.notification.config.NotificationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

/**
 * One token-bucket per channel, enforcing the configured send rate
 * (default: 2 mails/second, 10 in-app/second). The dispatcher must
 * {@link #tryAcquire} a token before delivering; if none is available it stops and
 * lets the remaining work wait for the next tick. Uses Bucket4j, same as the API
 * rate limiter.
 */
@Component
@RequiredArgsConstructor
public class NotificationRateLimiter {

    private final NotificationProperties properties;
    private final Map<NotificationChannel, Bucket> buckets = new EnumMap<>(NotificationChannel.class);

    @PostConstruct
    void init() {
        buckets.put(NotificationChannel.MAIL,        perSecondBucket(properties.getMail().getPerSecond()));
        buckets.put(NotificationChannel.APPLICATION, perSecondBucket(properties.getApp().getPerSecond()));
    }

    /** Try to take one token for the channel; false means the per-second limit is reached. */
    public boolean tryAcquire(NotificationChannel channel) {
        return buckets.get(channel).tryConsume(1);
    }

    private Bucket perSecondBucket(int perSecond) {
        int rate = Math.max(1, perSecond);
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(rate)
                        .refillGreedy(rate, Duration.ofSeconds(1))
                        .build())
                .build();
    }
}
