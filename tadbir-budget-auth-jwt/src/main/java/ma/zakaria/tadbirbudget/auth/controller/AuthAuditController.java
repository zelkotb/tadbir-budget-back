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
package ma.zakaria.tadbirbudget.auth.controller;

import lombok.RequiredArgsConstructor;
import ma.zakaria.tadbirbudget.auth.dto.AuthAuditResponse;
import ma.zakaria.tadbirbudget.auth.service.AuthAuditService;
import ma.zakaria.tadbirbudget.constant.Roles;
import ma.zakaria.tadbirbudget.entity.enums.AuthEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/audit")
@RequiredArgsConstructor
public class AuthAuditController {

    private final AuthAuditService authAuditService;

    /**
     * GET /api/v1/auth/audit
     *
     * <p>All query params are optional — omit to return the full audit log.
     *
     * @param email     partial email filter, case-insensitive (e.g. "john" matches "john@example.com")
     * @param ipAddress partial IP filter (e.g. "192.168" matches "192.168.1.1")
     * @param eventType filter by event type (LOGIN, LOGOUT, TOKEN_REFRESH)
     * @param success   filter by outcome (true = successful, false = failed)
     * @param date      partial date filter in DD/MM/YYYY format — any prefix works:
     *                  "04/06/2026" (exact day), "04/06/" (day+month), "04/0" (partial month)
     * @param pageable  page / size / sort — defaults: 20 records, newest first
     */
    @GetMapping
    @PreAuthorize(Roles.IS_ADMIN)
    public ResponseEntity<Page<AuthAuditResponse>> getAudit(
            @RequestParam(required = false) String        email,
            @RequestParam(required = false) String        ipAddress,
            @RequestParam(required = false) AuthEventType eventType,
            @RequestParam(required = false) Boolean       success,
            @RequestParam(required = false) String        date,
            @PageableDefault(size = 20, sort = "occurredAt", direction = Sort.Direction.DESC)
            Pageable pageable) {

        return ResponseEntity.ok(authAuditService.query(email, ipAddress, eventType, success, date, pageable));
    }
}