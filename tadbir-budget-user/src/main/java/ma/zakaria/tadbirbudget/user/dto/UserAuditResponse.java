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
package ma.zakaria.tadbirbudget.user.dto;

import ma.zakaria.tadbirbudget.entity.RevInfo;
import ma.zakaria.tadbirbudget.entity.User;
import ma.zakaria.tadbirbudget.enums.AuditAction;
import org.hibernate.envers.RevisionType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record UserAuditResponse(
        int            revisionId,
        Instant        occurredAt,
        String         performedBy,
        String         performedFrom,
        AuditAction    action,
        UUID           userId,
        String         uid,
        String         email,
        String         fullName,
        String         phoneNumber,
        boolean        enabled,
        List<String>   roles,
        UUID           orgUnitId
) {
    public static UserAuditResponse from(User user, RevInfo rev, RevisionType type) {
        AuditAction action = switch (type) {
            case ADD -> AuditAction.CREATE;
            case MOD -> AuditAction.UPDATE;
            case DEL -> AuditAction.DELETE;
        };
        return new UserAuditResponse(
                rev.getId(),
                Instant.ofEpochMilli(rev.getTimestamp()),
                rev.getActor(),
                rev.getIp(),
                action,
                user != null ? user.getId()              : null,
                user != null ? user.getUid()             : null,
                user != null ? user.getEmail()           : null,
                user != null ? user.getFullName()        : null,
                user != null ? user.getPhoneNumber()     : null,
                user != null && user.isEnabled(),
                user != null ? user.getRoles()           : List.of(),
                user != null ? user.getOrgUnitId()       : null
        );
    }
}