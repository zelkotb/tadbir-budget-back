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

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Enables ShedLock so that, with multiple app instances, only ONE runs the notification
 * dispatcher tick at a time. This both prevents the same message from being sent twice
 * and keeps the per-channel rate limits GLOBAL (a single active dispatcher), rather than
 * per-instance.
 *
 * <p>{@code defaultLockAtMostFor} is the safety ceiling: if the holding instance dies
 * mid-tick, the lock auto-releases after this long so another instance can take over.
 * {@code usingDbTime()} uses the database clock for lock timing, avoiding cross-node
 * clock skew.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT1M")
public class NotificationSchedulerConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build());
    }
}
