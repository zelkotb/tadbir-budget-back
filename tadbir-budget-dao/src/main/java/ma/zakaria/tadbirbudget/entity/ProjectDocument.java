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
package ma.zakaria.tadbirbudget.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * A document attached to a {@link Project} — any file type (PDF, Office, images, CAD, …). The bytes
 * live on the filesystem (via the files module); this row keeps the storage key ({@code filePath})
 * plus the original name / type / size and who uploaded it when.
 */
@Entity
@Table(name = "project_document")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "project_id", nullable = false, columnDefinition = "uuid")
    private UUID projectId;

    /** Relative storage key returned by the files module (used to download/delete the bytes). */
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "content_type", length = 255)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    /** Optional human label / description for the document. */
    @Column(length = 255)
    private String label;

    /** uid of the uploader. */
    @Column(name = "uploaded_by", length = 255)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;
}
