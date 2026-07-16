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
package ma.zakaria.tadbirbudget.workflow.controller;

import lombok.RequiredArgsConstructor;
import ma.zakaria.tadbirbudget.constant.Roles;
import ma.zakaria.tadbirbudget.workflow.dto.DeploymentResult;
import ma.zakaria.tadbirbudget.workflow.dto.ProcessDefinitionDto;
import ma.zakaria.tadbirbudget.workflow.service.WorkflowDefinitionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * Admin/designer API to create and update BPMN workflows. "Create" and "update" are both
 * a BPMN deployment (a new version of the process). Admin-only.
 */
@RestController
@RequestMapping("/api/v1/workflow/definitions")
@RequiredArgsConstructor
@PreAuthorize(Roles.IS_ADMIN)
public class WorkflowDefinitionController {

    private final WorkflowDefinitionService definitionService;

    /** POST /api/v1/workflow/definitions — deploy a BPMN file (create or new version). */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DeploymentResult> deploy(@RequestParam("file") MultipartFile file,
                                                   @RequestParam(required = false) String name) {
        return ResponseEntity.status(HttpStatus.CREATED).body(definitionService.deploy(name, file));
    }

    /** GET /api/v1/workflow/definitions — latest version of every process. */
    @GetMapping
    public ResponseEntity<List<ProcessDefinitionDto>> list() {
        return ResponseEntity.ok(definitionService.listLatest());
    }

    /** GET /api/v1/workflow/definitions/{key}/versions — all versions of one process. */
    @GetMapping("/{key}/versions")
    public ResponseEntity<List<ProcessDefinitionDto>> versions(@PathVariable String key) {
        return ResponseEntity.ok(definitionService.listVersions(key));
    }

    /** GET /api/v1/workflow/definitions/{id}/bpmn — raw BPMN XML of a definition. */
    @GetMapping(value = "/{id}/bpmn", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> bpmn(@PathVariable String id) {
        return ResponseEntity.ok(definitionService.getBpmnXml(id));
    }
}
