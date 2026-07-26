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
package ma.zakaria.tadbirbudget.project.controller;

import lombok.RequiredArgsConstructor;
import ma.zakaria.tadbirbudget.project.dto.DocumentDownload;
import ma.zakaria.tadbirbudget.project.dto.ProjectDocumentResponse;
import ma.zakaria.tadbirbudget.project.service.ProjectService;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * The documents section of a project — files of any type (PDF, Office, images, CAD, …). Listing and
 * download follow the project's read scope; upload and delete are limited to the chef de projet, a
 * team member, or a manager in scope (enforced in the service).
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/documents")
@RequiredArgsConstructor
public class ProjectDocumentController {

    private final ProjectService service;

    /** GET — list the project's documents (metadata only). */
    @GetMapping
    public ResponseEntity<List<ProjectDocumentResponse>> list(@PathVariable UUID projectId) {
        return ResponseEntity.ok(service.listDocuments(projectId));
    }

    /** POST (multipart) — attach a file, with an optional {@code label}. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ProjectDocumentResponse> upload(
            @PathVariable UUID projectId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "label", required = false) String label) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.uploadDocument(projectId, file, label));
    }

    /** GET — download the raw bytes of one document. */
    @GetMapping("/{documentId}")
    public ResponseEntity<Resource> download(@PathVariable UUID projectId, @PathVariable UUID documentId) {
        DocumentDownload dl = service.downloadDocument(projectId, documentId);
        String filename = UriUtils.encode(dl.filename(), StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + dl.filename() + "\"; filename*=UTF-8''" + filename)
                .contentType(MediaType.parseMediaType(dl.contentType()))
                .body(dl.resource());
    }

    /** DELETE — remove one document (row + stored bytes). */
    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId, @PathVariable UUID documentId) {
        service.deleteDocument(projectId, documentId);
        return ResponseEntity.noContent().build();
    }
}
