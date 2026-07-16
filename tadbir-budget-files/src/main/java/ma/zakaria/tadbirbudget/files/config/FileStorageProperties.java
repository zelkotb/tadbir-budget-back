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
package ma.zakaria.tadbirbudget.files.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * File storage tuning, under the {@code files.*} prefix. Files are written under
 * {@link #basePath}; uploads are rejected when empty, larger than {@link #maxSizeBytes},
 * or with a disallowed extension/content-type.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "files")
public class FileStorageProperties {

    /** Root directory where files are stored. */
    private String basePath = "D:/projects/tadbir-files";

    /** Max accepted file size in bytes (default 10 MB). */
    private long maxSizeBytes = 10L * 1024 * 1024;

    /** Allowed lowercase extensions (without dot). Empty = allow any extension. */
    private List<String> allowedExtensions = List.of(
            "pdf", "png", "jpg", "jpeg", "doc", "docx", "xls", "xlsx");

    /** Allowed content types. Empty = skip the content-type check (extension still applies). */
    private List<String> allowedContentTypes = List.of();
}
