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

import ma.zakaria.tadbirbudget.entity.ProjectDocument;

import java.time.Instant;
import java.util.UUID;

/** Metadata for a project document. The bytes are fetched via the download endpoint. */
public record ProjectDocumentResponse(
        UUID    id,
        UUID    projectId,
        String  originalFileName,
        String  contentType,
        long    sizeBytes,
        String  label,
        String  uploadedBy,
        Instant uploadedAt
) {
    public static ProjectDocumentResponse from(ProjectDocument d) {
        return new ProjectDocumentResponse(d.getId(), d.getProjectId(), d.getOriginalFileName(),
                d.getContentType(), d.getSizeBytes(), d.getLabel(), d.getUploadedBy(), d.getUploadedAt());
    }
}
