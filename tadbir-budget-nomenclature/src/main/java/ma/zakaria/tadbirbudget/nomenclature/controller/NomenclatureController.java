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
package ma.zakaria.tadbirbudget.nomenclature.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.zakaria.tadbirbudget.constant.Roles;
import ma.zakaria.tadbirbudget.nomenclature.dto.CreateNomenclatureInput;
import ma.zakaria.tadbirbudget.nomenclature.dto.NomenclatureResponse;
import ma.zakaria.tadbirbudget.nomenclature.dto.UpdateNomenclatureInput;
import ma.zakaria.tadbirbudget.nomenclature.service.NomenclatureService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Nomenclatures (the real trees) and their lifecycle. Reads are open to any authenticated user;
 * writes are restricted to admins and contrôle de gestion.
 */
@RestController
@RequestMapping("/api/v1/budget/nomenclatures")
@RequiredArgsConstructor
public class NomenclatureController {

    private final NomenclatureService service;

    /** GET /api/v1/budget/nomenclatures — all nomenclatures with their definition summary. */
    @GetMapping
    public ResponseEntity<List<NomenclatureResponse>> listAll() {
        return ResponseEntity.ok(service.listAll());
    }

    /** GET /api/v1/budget/nomenclatures/{id} — one nomenclature. */
    @GetMapping("/{id}")
    public ResponseEntity<NomenclatureResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    /** GET /api/v1/budget/nomenclatures/{id}/versions — all versions of this nomenclature's lineage. */
    @GetMapping("/{id}/versions")
    public ResponseEntity<List<NomenclatureResponse>> versions(@PathVariable UUID id) {
        return ResponseEntity.ok(service.versions(id));
    }

    /** POST /api/v1/budget/nomenclatures — create against a definition (DRAFT). Admin/CdG. */
    @PostMapping
    @PreAuthorize(Roles.IS_ADMIN_OR_CG)
    public ResponseEntity<NomenclatureResponse> create(@Valid @RequestBody CreateNomenclatureInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(input));
    }

    /** PATCH /api/v1/budget/nomenclatures/{id} — update name/description. Admin/CdG. */
    @PatchMapping("/{id}")
    @PreAuthorize(Roles.IS_ADMIN_OR_CG)
    public ResponseEntity<NomenclatureResponse> update(@PathVariable UUID id,
                                                       @Valid @RequestBody UpdateNomenclatureInput input) {
        return ResponseEntity.ok(service.update(id, input));
    }

    /** POST /api/v1/budget/nomenclatures/{id}/fix — lock the tree (DRAFT → FIXED). Admin/CdG. */
    @PostMapping("/{id}/fix")
    @PreAuthorize(Roles.IS_ADMIN_OR_CG)
    public ResponseEntity<NomenclatureResponse> fix(@PathVariable UUID id) {
        return ResponseEntity.ok(service.fix(id));
    }

    /**
     * POST /api/v1/budget/nomenclatures/{id}/clone?copyAssignments=false — clone a FIXED
     * nomenclature into a new DRAFT version of the same lineage. Admin/CdG.
     */
    @PostMapping("/{id}/clone")
    @PreAuthorize(Roles.IS_ADMIN_OR_CG)
    public ResponseEntity<NomenclatureResponse> clone(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "false") boolean copyAssignments) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.clone(id, copyAssignments));
    }

    /** POST /api/v1/budget/nomenclatures/{id}/archive — retire (→ ARCHIVED). Admin/CdG. */
    @PostMapping("/{id}/archive")
    @PreAuthorize(Roles.IS_ADMIN_OR_CG)
    public ResponseEntity<NomenclatureResponse> archive(@PathVariable UUID id) {
        return ResponseEntity.ok(service.archive(id));
    }

    /** DELETE /api/v1/budget/nomenclatures/{id} — delete a DRAFT nomenclature + its tree. Admin/CdG. */
    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.IS_ADMIN_OR_CG)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
