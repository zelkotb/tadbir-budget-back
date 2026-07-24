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
import ma.zakaria.tadbirbudget.constant.Roles;
import ma.zakaria.tadbirbudget.entity.enums.ProjectStatus;
import ma.zakaria.tadbirbudget.project.dto.CreateProjectInput;
import ma.zakaria.tadbirbudget.project.dto.ProjectResponse;
import ma.zakaria.tadbirbudget.project.dto.SetTeamInput;
import ma.zakaria.tadbirbudget.project.dto.StartProjectInput;
import ma.zakaria.tadbirbudget.project.dto.TerminateProjectInput;
import ma.zakaria.tadbirbudget.project.dto.UpdateProjectInput;
import ma.zakaria.tadbirbudget.project.service.ProjectService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Projects / programs. Reads are open to any authenticated user but <b>scoped</b> in the service
 * (managers see their org subtree; ADMIN / DIRECTION_GENERALE / CONTROLE_GESTION see everything;
 * a project's chef/members always see it). Writes require the project-creator roles and pass the
 * org-scope checkpoint in the service.
 */
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService service;

    /** GET /api/v1/projects?status=&orgUnitId= — scoped list. */
    @GetMapping
    public ResponseEntity<List<ProjectResponse>> list(
            @RequestParam(required = false) ProjectStatus status,
            @RequestParam(required = false) UUID orgUnitId) {
        return ResponseEntity.ok(service.list(status, orgUnitId));
    }

    /** GET /api/v1/projects/{id} — one project (with team). */
    @GetMapping("/{id}")
    public ResponseEntity<ProjectResponse> get(@PathVariable UUID id) {
        return ResponseEntity.ok(service.get(id));
    }

    /** POST /api/v1/projects — create in an org unit within the caller's subtree. */
    @PostMapping
    @PreAuthorize(Roles.IS_PROJECT_CREATOR)
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectInput input) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(input));
    }

    /** PATCH /api/v1/projects/{id} — update metadata. */
    @PatchMapping("/{id}")
    @PreAuthorize(Roles.IS_PROJECT_CREATOR)
    public ResponseEntity<ProjectResponse> update(@PathVariable UUID id,
                                                  @Valid @RequestBody UpdateProjectInput input) {
        return ResponseEntity.ok(service.update(id, input));
    }

    /** PUT /api/v1/projects/{id}/team — replace the whole team. */
    @PutMapping("/{id}/team")
    @PreAuthorize(Roles.IS_PROJECT_CREATOR)
    public ResponseEntity<ProjectResponse> setTeam(@PathVariable UUID id,
                                                   @Valid @RequestBody SetTeamInput input) {
        return ResponseEntity.ok(service.setTeam(id, input));
    }

    /**
     * POST /api/v1/projects/{id}/start — the responsible (chef de projet) or a manager in scope
     * starts a NOT_STARTED project (→ ACTIVE), recording the start date (today if the body is
     * omitted). Not gated by the creator roles here: the chef may be a plain employee — the service
     * enforces "chef or manager-in-scope".
     */
    @PostMapping("/{id}/start")
    public ResponseEntity<ProjectResponse> start(@PathVariable UUID id,
                                                 @Valid @RequestBody(required = false) StartProjectInput input) {
        return ResponseEntity.ok(service.start(id, input == null ? null : input.getStartDate()));
    }

    /** POST /api/v1/projects/{id}/terminate — mark TERMINATED as of {year}. */
    @PostMapping("/{id}/terminate")
    @PreAuthorize(Roles.IS_PROJECT_CREATOR)
    public ResponseEntity<ProjectResponse> terminate(@PathVariable UUID id,
                                                     @Valid @RequestBody TerminateProjectInput input) {
        return ResponseEntity.ok(service.terminate(id, input.getYear()));
    }

    /** POST /api/v1/projects/{id}/archive — retire the project. */
    @PostMapping("/{id}/archive")
    @PreAuthorize(Roles.IS_PROJECT_CREATOR)
    public ResponseEntity<ProjectResponse> archive(@PathVariable UUID id) {
        return ResponseEntity.ok(service.archive(id));
    }

    /** DELETE /api/v1/projects/{id} — delete the project and its team. */
    @DeleteMapping("/{id}")
    @PreAuthorize(Roles.IS_PROJECT_CREATOR)
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
