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
package ma.zakaria.tadbirbudget.settings.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.zakaria.tadbirbudget.constant.Roles;
import ma.zakaria.tadbirbudget.settings.dto.SettingResponse;
import ma.zakaria.tadbirbudget.settings.dto.UpdateSettingInput;
import ma.zakaria.tadbirbudget.settings.service.SettingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Company-wide settings (paramétrage). Reads are open to any authenticated user (the front reads
 * them to label the UI); updating is admin-only.
 */
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingController {

    private final SettingService service;

    /** GET /api/v1/settings — all settings. */
    @GetMapping
    public ResponseEntity<List<SettingResponse>> listAll() {
        return ResponseEntity.ok(service.listAll());
    }

    /** GET /api/v1/settings/{key} — one setting. */
    @GetMapping("/{key}")
    public ResponseEntity<SettingResponse> get(@PathVariable String key) {
        return ResponseEntity.ok(service.get(key));
    }

    /** PUT /api/v1/settings/{key} — update a known setting's value. Admin. */
    @PutMapping("/{key}")
    @PreAuthorize(Roles.IS_ADMIN)
    public ResponseEntity<SettingResponse> update(@PathVariable String key,
                                                  @Valid @RequestBody UpdateSettingInput input) {
        return ResponseEntity.ok(service.update(key, input.getValue()));
    }
}
