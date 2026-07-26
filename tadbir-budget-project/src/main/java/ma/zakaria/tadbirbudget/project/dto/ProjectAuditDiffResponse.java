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
package ma.zakaria.tadbirbudget.project.dto;

import ma.zakaria.tadbirbudget.enums.AuditAction;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Diff view of a single Project audit revision.
 *
 * For CREATE: every field shows before=null, after=&lt;new value&gt;.
 * For UPDATE: only changed fields are included.
 * For DELETE: every field shows before=&lt;last value&gt;, after=null.
 *             (Envers stores the final state on deletion via store_data_at_delete=true.)
 */
public record ProjectAuditDiffResponse(
        int               revisionId,
        Instant           occurredAt,
        String            performedBy,
        String            performedFrom,
        AuditAction       action,
        UUID              projectId,
        String            projectName,
        List<FieldChange> changes
) {
    public record FieldChange(String field, Object before, Object after) {}
}
