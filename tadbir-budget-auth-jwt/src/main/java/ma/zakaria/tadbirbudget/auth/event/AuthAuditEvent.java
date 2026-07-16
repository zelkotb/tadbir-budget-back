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
package ma.zakaria.tadbirbudget.auth.event;

import lombok.Getter;
import ma.zakaria.tadbirbudget.entity.enums.AuthEventType;

import java.time.Instant;

/**
 * Spring application event published after a successful auth operation.
 * Handled by {@link AuthAuditListener} after the originating transaction commits.
 */
@Getter
public class AuthAuditEvent {

    private final String        email;
    private final AuthEventType eventType;
    private final boolean       success;
    private final String        ipAddress;
    private final String        userAgent;
    private final Instant       occurredAt = Instant.now();

    public AuthAuditEvent(String email, AuthEventType eventType, boolean success,
                          String ipAddress, String userAgent) {
        this.email     = email;
        this.eventType = eventType;
        this.success   = success;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }
}