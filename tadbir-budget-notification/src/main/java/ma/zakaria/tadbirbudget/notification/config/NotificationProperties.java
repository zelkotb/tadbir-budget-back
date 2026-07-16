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
package ma.zakaria.tadbirbudget.notification.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All notification tuning lives here, under the {@code notification.*} prefix in
 * application.yaml. Defaults are production-sane; override per environment.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    /** Master switch: when false the background dispatcher does nothing (queue still fills). */
    private boolean enabled = true;

    private final Mail     mail     = new Mail();
    private final App      app      = new App();
    private final Dispatch dispatch = new Dispatch();
    private final Backoff  backoff  = new Backoff();

    @Getter
    @Setter
    public static class Mail {
        /** When false, e-mails are logged instead of sent (handy for dev/test with no SMTP). */
        private boolean enabled = false;
        /** From address on outgoing mail. */
        private String from = "no-reply@tadbir-budget.ma";
        /** Throttle: max e-mails delivered per second. */
        private int perSecond = 2;
    }

    @Getter
    @Setter
    public static class App {
        /** Throttle: max in-app notifications delivered per second. */
        private int perSecond = 10;
    }

    @Getter
    @Setter
    public static class Dispatch {
        /** How often the dispatcher wakes up to drain the queue (milliseconds). */
        private long fixedDelayMs = 1000;
        /** Max rows fetched per channel per tick (a safety cap above the per-second rate). */
        private int batchSize = 100;
    }

    @Getter
    @Setter
    public static class Backoff {
        /** Give up (mark FAILED) after this many failed attempts. */
        private int maxAttempts = 5;
        /** Delay before the first retry, in seconds; doubles (×multiplier) each subsequent retry. */
        private long initialSeconds = 30;
        /** Growth factor applied to the delay after each failure. */
        private double multiplier = 2.0;
    }
}
