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
package ma.zakaria.tadbirbudget.auth.dto;

import ma.zakaria.tadbirbudget.entity.AuthAudit;
import ma.zakaria.tadbirbudget.entity.enums.AuthEventType;

import java.time.Instant;
import java.util.UUID;

public record AuthAuditResponse(
        UUID          id,
        String        email,
        AuthEventType eventType,
        boolean       success,
        String        ipAddress,
        String        userAgent,
        Instant       occurredAt
) {
    public static AuthAuditResponse from(AuthAudit audit) {
        return new AuthAuditResponse(
                audit.getId(),
                audit.getEmail(),
                audit.getEventType(),
                audit.isSuccess(),
                audit.getIpAddress(),
                audit.getUserAgent(),
                audit.getOccurredAt()
        );
    }
}