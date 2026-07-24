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
import ma.zakaria.tadbirbudget.nomenclature.dto.CreateRubriqueAssignmentInput;
import ma.zakaria.tadbirbudget.nomenclature.dto.RubriqueAssignmentResponse;
import ma.zakaria.tadbirbudget.nomenclature.dto.RubriqueResponse;
import ma.zakaria.tadbirbudget.nomenclature.service.RubriqueAssignmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Rubrique → org-unit assignments for a nomenclature, and the "usable rubriques" resolver.
 * Managing assignments is admin / contrôle de gestion; the usable-rubriques resolver is open to any
 * authenticated user (it answers "what may I attach to a project" for the caller's own org unit).
 */
@RestController
@RequestMapping("/api/v1/budget/nomenclatures/{nomenclatureId}")
@RequiredArgsConstructor
public class RubriqueAssignmentController {

    private final RubriqueAssignmentService service;

    /** GET .../assignments?orgUnitId= — assignments in this nomenclature (optionally one unit). CG/admin. */
    @GetMapping("/assignments")
    @PreAuthorize(Roles.IS_ADMIN_OR_CG)
    public ResponseEntity<List<RubriqueAssignmentResponse>> list(
            @PathVariable UUID nomenclatureId,
            @RequestParam(required = false) UUID orgUnitId) {
        return ResponseEntity.ok(service.listAssignments(nomenclatureId, orgUnitId));
    }

    /** POST .../assignments — assign a rubrique to an org unit (nomenclature must be FIXED). CG/admin. */
    @PostMapping("/assignments")
    @PreAuthorize(Roles.IS_ADMIN_OR_CG)
    public ResponseEntity<RubriqueAssignmentResponse> assign(
            @PathVariable UUID nomenclatureId,
            @Valid @RequestBody CreateRubriqueAssignmentInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.assign(nomenclatureId, input));
    }

    /** DELETE .../assignments/{assignmentId} — remove an assignment. CG/admin. */
    @DeleteMapping("/assignments/{assignmentId}")
    @PreAuthorize(Roles.IS_ADMIN_OR_CG)
    public ResponseEntity<Void> unassign(@PathVariable UUID nomenclatureId,
                                         @PathVariable UUID assignmentId) {
        service.unassign(nomenclatureId, assignmentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET .../usable-rubriques?orgUnitId= — the rubriques the caller may attach to a project (assigned
     * to their org unit or any ancestor, plus each assigned node's subtree). Admins may pass
     * {@code orgUnitId} to preview another unit. Any authenticated user.
     */
    @GetMapping("/usable-rubriques")
    public ResponseEntity<List<RubriqueResponse>> usableRubriques(
            @PathVariable UUID nomenclatureId,
            @RequestParam(required = false) UUID orgUnitId) {
        return ResponseEntity.ok(service.usableRubriques(nomenclatureId, orgUnitId));
    }
}
