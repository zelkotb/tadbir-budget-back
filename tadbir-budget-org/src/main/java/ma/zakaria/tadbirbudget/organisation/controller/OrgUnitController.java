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
package ma.zakaria.tadbirbudget.organisation.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.zakaria.tadbirbudget.constant.Roles;
import ma.zakaria.tadbirbudget.organisation.dto.CreateOrgUnitInput;
import ma.zakaria.tadbirbudget.organisation.dto.OrgUnitResponse;
import ma.zakaria.tadbirbudget.organisation.dto.OrgUnitUserResponse;
import ma.zakaria.tadbirbudget.organisation.dto.UpdateOrgUnitInput;
import ma.zakaria.tadbirbudget.organisation.service.OrgUnitService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Organisation structure API. Reads are open to any authenticated user (the org tree is
 * reference data used everywhere); writes are admin-only.
 */
@RestController
@RequestMapping("/api/v1/org-units")
@RequiredArgsConstructor
public class OrgUnitController {

    private final OrgUnitService orgUnitService;

    /**
     * GET /api/v1/org-units
     * Flat list of every unit in tree order (a parent always precedes its children) — the
     * client rebuilds the tree from {@code parentId}/{@code depth}.
     */
    @GetMapping
    public ResponseEntity<List<OrgUnitResponse>> listAll() {
        return ResponseEntity.ok(orgUnitService.listAll());
    }

    /** GET /api/v1/org-units/{id} — one unit. */
    @GetMapping("/{id}")
    public ResponseEntity<OrgUnitResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(orgUnitService.get(id));
    }

    /** GET /api/v1/org-units/{id}/subtree — the unit and all its descendants, tree order. */
    @GetMapping("/{id}/subtree")
    public ResponseEntity<List<OrgUnitResponse>> subtree(@PathVariable UUID id) {
        return ResponseEntity.ok(orgUnitService.subtree(id));
    }

    /**
     * GET /api/v1/org-units/{id}/users?subtree=false
     * Users assigned to the unit; {@code subtree=true} includes every descendant unit's users.
     */
    @GetMapping("/{id}/users")
    public ResponseEntity<List<OrgUnitUserResponse>> users(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean subtree) {
        return ResponseEntity.ok(orgUnitService.users(id, subtree));
    }

    /** POST /api/v1/org-units — create a unit (root when {@code parentId} is null). Admin. */
    @PostMapping
    @PreAuthorize(Roles.IS_ADMIN)
    public ResponseEntity<OrgUnitResponse> create(@Valid @RequestBody CreateOrgUnitInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(orgUnitService.create(input));
    }

    /**
     * PATCH /api/v1/org-units/{id} — partial update; may also move the node ({@code parentId}
     * or {@code moveToRoot}), taking its whole subtree along. Admin.
     */
    @PatchMapping("/{id}")
    @PreAuthorize(Roles.IS_ADMIN)
    public ResponseEntity<OrgUnitResponse> update(@PathVariable UUID id,
                                                  @Valid @RequestBody UpdateOrgUnitInput input) {
        return ResponseEntity.ok(orgUnitService.update(id, input));
    }

    /**
     * DELETE /api/v1/org-units/{id} — delete a unit. Refused ({@code ORG_UNIT_HAS_CHILDREN} /
     * {@code ORG_UNIT_HAS_USERS}) while children or assigned users remain. Admin.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.IS_ADMIN)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        orgUnitService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
