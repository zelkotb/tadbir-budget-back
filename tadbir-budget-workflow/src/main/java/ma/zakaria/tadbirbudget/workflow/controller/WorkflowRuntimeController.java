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

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.zakaria.tadbirbudget.constant.Roles;
import ma.zakaria.tadbirbudget.util.SecurityUtils;
import ma.zakaria.tadbirbudget.workflow.dto.CompleteTaskRequest;
import ma.zakaria.tadbirbudget.workflow.dto.ProcessInstanceDto;
import ma.zakaria.tadbirbudget.workflow.dto.ProcessStepDto;
import ma.zakaria.tadbirbudget.workflow.dto.StartProcessRequest;
import ma.zakaria.tadbirbudget.workflow.dto.TaskDto;
import ma.zakaria.tadbirbudget.workflow.service.WorkflowRuntimeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Generic runtime API across every BPMN workflow: start an instance, the personal task
 * inbox, claim/unclaim (reservation), and complete (forward / send-back). Users are keyed
 * by email; candidate groups are the caller's role authorities.
 *
 * <p>Restricted to workflow actors (instructor / pôle-directeur / admin) — citizens never act
 * on tasks directly. Fine-grained "who may claim <em>this</em> task" is enforced per-task by
 * the service from the BPMN candidate groups.
 */
@RestController
@RequestMapping("/api/v1/workflow")
@RequiredArgsConstructor
@PreAuthorize(Roles.IS_WORKFLOW_ACTOR)
public class WorkflowRuntimeController {

    private final WorkflowRuntimeService runtimeService;

    /** POST /api/v1/workflow/instances — start a process instance. */
    @PostMapping("/instances")
    public ResponseEntity<ProcessInstanceDto> start(@Valid @RequestBody StartProcessRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(runtimeService.start(request, currentUserId()));
    }

    /** GET /api/v1/workflow/instances?businessKey=… — instances for a domain record. */
    @GetMapping("/instances")
    public ResponseEntity<List<ProcessInstanceDto>> byBusinessKey(@RequestParam String businessKey) {
        return ResponseEntity.ok(runtimeService.byBusinessKey(businessKey));
    }

    /**
     * GET /api/v1/workflow/instances/history?businessKey=… — ordered step (user-task) timeline
     * for a record's process, including completed and active steps and any rework loops.
     */
    @GetMapping("/instances/history")
    public ResponseEntity<List<ProcessStepDto>> stepHistory(@RequestParam String businessKey) {
        return ResponseEntity.ok(runtimeService.processStepHistory(businessKey));
    }

    /** GET /api/v1/workflow/tasks/inbox — tasks assigned to me + ones my roles can claim. */
    @GetMapping("/tasks/inbox")
    public ResponseEntity<List<TaskDto>> inbox() {
        return ResponseEntity.ok(runtimeService.inbox(currentUserId(), currentGroups()));
    }

    /**
     * POST /api/v1/workflow/tasks/{taskId}/claim — reserve a task for me. The service permits
     * this only if one of my roles is a candidate group of the task (e.g. only an instructor
     * may claim the instruction task).
     */
    @PostMapping("/tasks/{taskId}/claim")
    public ResponseEntity<Void> claim(@PathVariable String taskId) {
        runtimeService.claim(taskId, currentUserId(), currentGroups());
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/workflow/tasks/{taskId}/unclaim — release a reservation. Only an instructor may
     * unclaim, and only their own task.
     */
    @PostMapping("/tasks/{taskId}/unclaim")
    public ResponseEntity<Void> unclaim(@PathVariable String taskId) {
        runtimeService.unclaim(taskId, currentUserId(), currentGroups());
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/workflow/tasks/{taskId}/reassign?userId=… — admin hands the task to another
     * user who holds the task's required role (same role). Admin only.
     */
    @PostMapping("/tasks/{taskId}/reassign")
    @PreAuthorize(Roles.IS_ADMIN)
    public ResponseEntity<Void> reassign(@PathVariable String taskId, @RequestParam java.util.UUID userId) {
        runtimeService.reassign(taskId, userId);
        return ResponseEntity.noContent().build();
    }

    /** POST /api/v1/workflow/tasks/{taskId}/complete — complete with routing variables. */
    @PostMapping("/tasks/{taskId}/complete")
    public ResponseEntity<Void> complete(@PathVariable String taskId,
                                         @RequestBody CompleteTaskRequest request) {
        runtimeService.complete(taskId, request, currentUserId());
        return ResponseEntity.noContent().build();
    }

    private String currentUserId() {
        return SecurityUtils.getCurrentUserEmail();
    }

    private List<String> currentGroups() {
        return SecurityUtils.getCurrentUser().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }
}
