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
import ma.zakaria.tadbirbudget.constant.Permissions;
import ma.zakaria.tadbirbudget.nomenclature.dto.CreateNomenclatureDefinitionInput;
import ma.zakaria.tadbirbudget.nomenclature.dto.NomenclatureDefinitionResponse;
import ma.zakaria.tadbirbudget.nomenclature.dto.UpdateNomenclatureDefinitionInput;
import ma.zakaria.tadbirbudget.nomenclature.service.NomenclatureDefinitionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Budget nomenclature definitions (the level templates: Chapitre → Article → … → Ligne). Both reading
 * and writing require the budget-definition permission (admin always) — see {@link Permissions}.
 */
@RestController
@RequestMapping("/api/v1/budget/nomenclature-definitions")
@RequiredArgsConstructor
@PreAuthorize(Permissions.CAN_MANAGE_BUDGET_DEFINITION)
public class NomenclatureDefinitionController {

    private final NomenclatureDefinitionService service;

    /** GET /api/v1/budget/nomenclature-definitions — all definitions with their ordered levels. */
    @GetMapping
    public ResponseEntity<List<NomenclatureDefinitionResponse>> listAll() {
        return ResponseEntity.ok(service.listAll());
    }

    /** GET /api/v1/budget/nomenclature-definitions/{id} — one definition with its levels. */
    @GetMapping("/{id}")
    public ResponseEntity<NomenclatureDefinitionResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    /** POST /api/v1/budget/nomenclature-definitions — create with its ordered level names. Admin/CdG. */
    @PostMapping
    public ResponseEntity<NomenclatureDefinitionResponse> create(
            @Valid @RequestBody CreateNomenclatureDefinitionInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(input));
    }

    /**
     * PATCH /api/v1/budget/nomenclature-definitions/{id} — update name/description/active and/or
     * replace the whole level list. Admin/CdG.
     */
    @PatchMapping("/{id}")
    public ResponseEntity<NomenclatureDefinitionResponse> update(
            @PathVariable UUID id, @Valid @RequestBody UpdateNomenclatureDefinitionInput input) {
        return ResponseEntity.ok(service.update(id, input));
    }

    /** DELETE /api/v1/budget/nomenclature-definitions/{id} — delete a definition + its levels. Admin/CdG. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
