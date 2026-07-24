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
import ma.zakaria.tadbirbudget.nomenclature.dto.CreateRubriqueInput;
import ma.zakaria.tadbirbudget.nomenclature.dto.RubriqueResponse;
import ma.zakaria.tadbirbudget.nomenclature.dto.UpdateRubriqueInput;
import ma.zakaria.tadbirbudget.nomenclature.service.NomenclatureRubriqueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * The rubrique tree of a nomenclature. Reads are open; writes require the nomenclature to be DRAFT
 * and the caller to be admin / contrôle de gestion. The client builds the tree from {@code parentId}.
 */
@RestController
@RequestMapping("/api/v1/budget/nomenclatures/{nomenclatureId}/rubriques")
@RequiredArgsConstructor
public class NomenclatureRubriqueController {

    private final NomenclatureRubriqueService service;

    /** GET — the flat list of rubriques (build the tree from parentId). */
    @GetMapping
    public ResponseEntity<List<RubriqueResponse>> list(@PathVariable UUID nomenclatureId) {
        return ResponseEntity.ok(service.list(nomenclatureId));
    }

    /** POST — add a rubrique ({@code parentId} null = top level). Admin/CdG, DRAFT only. */
    @PostMapping
    @PreAuthorize(Roles.IS_ADMIN_OR_CG)
    public ResponseEntity<RubriqueResponse> create(@PathVariable UUID nomenclatureId,
                                                   @Valid @RequestBody CreateRubriqueInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(nomenclatureId, input));
    }

    /** PATCH — update a rubrique's code/label. Admin/CdG, DRAFT only. */
    @PatchMapping("/{rubriqueId}")
    @PreAuthorize(Roles.IS_ADMIN_OR_CG)
    public ResponseEntity<RubriqueResponse> update(@PathVariable UUID nomenclatureId,
                                                   @PathVariable UUID rubriqueId,
                                                   @Valid @RequestBody UpdateRubriqueInput input) {
        return ResponseEntity.ok(service.update(nomenclatureId, rubriqueId, input));
    }

    /** DELETE — remove a leaf/childless rubrique. Admin/CdG, DRAFT only. */
    @DeleteMapping("/{rubriqueId}")
    @PreAuthorize(Roles.IS_ADMIN_OR_CG)
    public ResponseEntity<Void> delete(@PathVariable UUID nomenclatureId, @PathVariable UUID rubriqueId) {
        service.delete(nomenclatureId, rubriqueId);
        return ResponseEntity.noContent().build();
    }
}
