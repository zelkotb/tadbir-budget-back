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
package ma.zakaria.tadbirbudget.ratelimit;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private boolean enabled = true;

    /** Applied to /api/v1/auth/** — login, signup, refresh. */
    private BucketConfig auth = new BucketConfig(5, 5, 60);

    /** Applied to every other endpoint. */
    private BucketConfig api = new BucketConfig(100, 100, 60);

    @Getter
    @Setter
    public static class BucketConfig {

        /** Maximum tokens the bucket can hold (burst capacity). */
        private long capacity;

        /** Tokens restored per refill cycle. */
        private long refillTokens;

        /** Duration of one refill cycle in seconds. */
        private long refillSeconds;

        public BucketConfig() {}

        public BucketConfig(long capacity, long refillTokens, long refillSeconds) {
            this.capacity      = capacity;
            this.refillTokens  = refillTokens;
            this.refillSeconds = refillSeconds;
        }
    }
}
