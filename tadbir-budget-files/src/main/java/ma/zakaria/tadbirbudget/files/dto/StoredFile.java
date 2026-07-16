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
package ma.zakaria.tadbirbudget.files.dto;

/**
 * The result of storing a file. {@code path} is the relative key (under the storage root)
 * used to download/replace/delete the file later — return it to clients and persist it on
 * the owning record.
 */
public record StoredFile(
        String path,
        String originalFileName,
        String contentType,
        long sizeBytes) {
}
