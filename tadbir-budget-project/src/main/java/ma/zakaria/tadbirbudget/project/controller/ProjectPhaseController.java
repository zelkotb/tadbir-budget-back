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

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.zakaria.tadbirbudget.project.dto.CreatePhaseInput;
import ma.zakaria.tadbirbudget.project.dto.PhaseResponse;
import ma.zakaria.tadbirbudget.project.dto.ProjectPhaseKpiResponse;
import ma.zakaria.tadbirbudget.project.dto.UpdatePhaseInput;
import ma.zakaria.tadbirbudget.project.dto.UpdatePhaseStatusInput;
import ma.zakaria.tadbirbudget.project.service.ProjectKpiService;
import ma.zakaria.tadbirbudget.project.service.ProjectPhaseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Phases of a project — how it is followed step by step. Listing follows the project's read scope;
 * create / update / status / delete are limited to the chef de projet or a manager in scope
 * (enforced in the service). A terminated phase is read-only.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/phases")
@RequiredArgsConstructor
public class ProjectPhaseController {

    private final ProjectPhaseService service;
    private final ProjectKpiService   kpiService;

    /** GET — list the project's phases (ordered by schedule). */
    @GetMapping
    public ResponseEntity<List<PhaseResponse>> list(@PathVariable UUID projectId) {
        return ResponseEntity.ok(service.list(projectId));
    }

    /** GET — phase-level KPIs (one set per phase), computed from the phases (read scope). */
    @GetMapping("/kpis")
    public ResponseEntity<ProjectPhaseKpiResponse> kpis(@PathVariable UUID projectId) {
        return ResponseEntity.ok(kpiService.phaseKpis(projectId));
    }

    /** GET — one phase. */
    @GetMapping("/{phaseId}")
    public ResponseEntity<PhaseResponse> get(@PathVariable UUID projectId, @PathVariable UUID phaseId) {
        return ResponseEntity.ok(service.get(projectId, phaseId));
    }

    /** POST — create a phase (starts CREATED; captures the baseline schedule). */
    @PostMapping
    public ResponseEntity<PhaseResponse> create(@PathVariable UUID projectId,
                                                @Valid @RequestBody CreatePhaseInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(projectId, input));
    }

    /** GET — list a phase's sous-phases. */
    @GetMapping("/{phaseId}/subphases")
    public ResponseEntity<List<PhaseResponse>> listSubPhases(@PathVariable UUID projectId,
                                                             @PathVariable UUID phaseId) {
        return ResponseEntity.ok(service.listSubPhases(projectId, phaseId));
    }

    /** POST — create a sous-phase under a phase (same rules as a phase). */
    @PostMapping("/{phaseId}/subphases")
    public ResponseEntity<PhaseResponse> createSubPhase(@PathVariable UUID projectId,
                                                        @PathVariable UUID phaseId,
                                                        @Valid @RequestBody CreatePhaseInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createSubPhase(projectId, phaseId, input));
    }

    /** PATCH — update a phase's content (blocked once TERMINATED). */
    @PatchMapping("/{phaseId}")
    public ResponseEntity<PhaseResponse> update(@PathVariable UUID projectId, @PathVariable UUID phaseId,
                                                @Valid @RequestBody UpdatePhaseInput input) {
        return ResponseEntity.ok(service.update(projectId, phaseId, input));
    }

    /** PATCH — move the phase forward: CREATED → ACTIVE → TERMINATED. */
    @PatchMapping("/{phaseId}/status")
    public ResponseEntity<PhaseResponse> changeStatus(@PathVariable UUID projectId, @PathVariable UUID phaseId,
                                                      @Valid @RequestBody UpdatePhaseStatusInput input) {
        return ResponseEntity.ok(service.changeStatus(projectId, phaseId, input.getStatus()));
    }

    /** DELETE — remove a phase (blocked once TERMINATED). */
    @DeleteMapping("/{phaseId}")
    public ResponseEntity<Void> delete(@PathVariable UUID projectId, @PathVariable UUID phaseId) {
        service.delete(projectId, phaseId);
        return ResponseEntity.noContent().build();
    }
}
