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

import ma.zakaria.tadbirbudget.enums.AuditAction;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Diff view of a single User audit revision.
 *
 * For CREATE: every field shows before=null, after=<new value>.
 * For UPDATE: only changed fields are included.
 * For DELETE: every field shows before=<last value>, after=null.
 *             (Envers stores the final state on deletion via store_data_at_delete=true.)
 */
public record UserAuditDiffResponse(
        int               revisionId,
        Instant           occurredAt,
        String            performedBy,
        String            performedFrom,
        AuditAction       action,
        UUID              userId,
        String            userEmail,
        String            userFullName,
        List<FieldChange> changes
) {
    public record FieldChange(String field, Object before, Object after) {}
}
