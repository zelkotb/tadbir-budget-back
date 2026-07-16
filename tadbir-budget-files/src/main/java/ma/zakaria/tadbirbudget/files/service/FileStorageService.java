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
package ma.zakaria.tadbirbudget.files.service;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.zakaria.tadbirbudget.exception.CustomException;
import ma.zakaria.tadbirbudget.exception.ErrorCode;
import ma.zakaria.tadbirbudget.files.config.FileStorageProperties;
import ma.zakaria.tadbirbudget.files.dto.StoredFile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Stores files on the local filesystem under a configured root. The public {@code path}
 * returned/accepted everywhere is <b>relative to the root</b> (e.g. {@code pa-documents/uuid.pdf});
 * all path inputs are normalized and confined to the root to prevent traversal.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final FileStorageProperties properties;
    private Path root;

    @PostConstruct
    void init() {
        this.root = Paths.get(properties.getBasePath()).toAbsolutePath().normalize();
    }

    // ── Public API (reused by other modules) ───────────────────────────────────

    /** Validate and store one file under {@code subdir} (e.g. "pa-documents"); returns its path. */
    public StoredFile store(MultipartFile file, String subdir) {
        validate(file);
        String ext = extension(file.getOriginalFilename());
        String fileName = UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);

        String safeSubdir = sanitizeSubdir(subdir);
        Path dir = safeSubdir.isEmpty() ? root : resolveInsideRoot(safeSubdir);
        Path target = dir.resolve(fileName).normalize();
        ensureInsideRoot(target);
        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            log.error("Failed to store file {}", file.getOriginalFilename(), ex);
            throw new CustomException(ErrorCode.FILE_STORAGE_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
        String relative = toRelative(target);
        log.debug("Stored file {} -> {}", file.getOriginalFilename(), relative);
        return new StoredFile(relative, file.getOriginalFilename(), file.getContentType(), file.getSize());
    }

    /** Store several files at once. */
    public List<StoredFile> storeAll(List<MultipartFile> files, String subdir) {
        return files.stream().map(f -> store(f, subdir)).toList();
    }

    /** Load a stored file as a Resource for download. */
    public Resource load(String relativePath) {
        Path file = resolveInsideRoot(relativePath);
        try {
            Resource resource = new UrlResource(file.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new CustomException(ErrorCode.FILE_NOT_FOUND, HttpStatus.NOT_FOUND);
            }
            return resource;
        } catch (java.net.MalformedURLException ex) {
            throw new CustomException(ErrorCode.FILE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
    }

    /** Replace the file at {@code relativePath}: deletes the old one and stores the new one (new path). */
    public StoredFile replace(String relativePath, MultipartFile newFile) {
        delete(relativePath);
        String subdir = parentSubdir(relativePath);
        return store(newFile, subdir);
    }

    /** Delete the file at {@code relativePath} (no error if it does not exist). */
    public void delete(String relativePath) {
        Path file = resolveInsideRoot(relativePath);
        try {
            Files.deleteIfExists(file);
        } catch (IOException ex) {
            log.error("Failed to delete file {}", relativePath, ex);
            throw new CustomException(ErrorCode.FILE_STORAGE_ERROR, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /** Best-effort content type for a stored file (for the download response). */
    public String probeContentType(String relativePath) {
        try {
            String type = Files.probeContentType(resolveInsideRoot(relativePath));
            return type != null ? type : "application/octet-stream";
        } catch (IOException ex) {
            return "application/octet-stream";
        }
    }

    // ── Validation & path safety ───────────────────────────────────────────────

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.FILE_EMPTY, HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > properties.getMaxSizeBytes()) {
            throw new CustomException(ErrorCode.FILE_TOO_LARGE, HttpStatus.PAYLOAD_TOO_LARGE);
        }
        List<String> allowedExt = properties.getAllowedExtensions();
        if (!allowedExt.isEmpty()) {
            String ext = extension(file.getOriginalFilename());
            if (ext.isEmpty() || !allowedExt.contains(ext)) {
                throw new CustomException(ErrorCode.FILE_TYPE_NOT_ALLOWED, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
            }
        }
        List<String> allowedTypes = properties.getAllowedContentTypes();
        if (!allowedTypes.isEmpty()
                && (file.getContentType() == null || !allowedTypes.contains(file.getContentType()))) {
            throw new CustomException(ErrorCode.FILE_TYPE_NOT_ALLOWED, HttpStatus.UNSUPPORTED_MEDIA_TYPE);
        }
    }

    private Path resolveInsideRoot(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            throw new CustomException(ErrorCode.INVALID_FILE_PATH, HttpStatus.BAD_REQUEST);
        }
        Path resolved = root.resolve(relativePath).normalize();
        ensureInsideRoot(resolved);
        return resolved;
    }

    private void ensureInsideRoot(Path path) {
        if (!path.startsWith(root)) {
            throw new CustomException(ErrorCode.INVALID_FILE_PATH, HttpStatus.BAD_REQUEST);
        }
    }

    private String sanitizeSubdir(String subdir) {
        if (subdir == null || subdir.isBlank()) {
            return "";
        }
        // strip any traversal / absolute markers; keep simple folder names
        return StringUtils.cleanPath(subdir).replace("..", "").replaceAll("^/+", "");
    }

    private String parentSubdir(String relativePath) {
        Path parent = Paths.get(relativePath).getParent();
        return parent == null ? "" : parent.toString();
    }

    private String toRelative(Path target) {
        return root.relativize(target).toString().replace('\\', '/');
    }

    private String extension(String filename) {
        String ext = StringUtils.getFilenameExtension(filename);
        return ext == null ? "" : ext.toLowerCase(Locale.ROOT);
    }
}
