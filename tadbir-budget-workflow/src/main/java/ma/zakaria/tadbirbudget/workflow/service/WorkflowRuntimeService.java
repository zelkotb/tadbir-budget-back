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
package ma.zakaria.tadbirbudget.workflow.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.zakaria.tadbirbudget.constant.Roles;
import ma.zakaria.tadbirbudget.entity.User;
import ma.zakaria.tadbirbudget.exception.CustomException;
import ma.zakaria.tadbirbudget.exception.ErrorCode;
import ma.zakaria.tadbirbudget.repository.UserRepository;
import ma.zakaria.tadbirbudget.workflow.dto.CompleteTaskRequest;
import ma.zakaria.tadbirbudget.workflow.dto.ProcessInstanceDto;
import ma.zakaria.tadbirbudget.workflow.dto.StartProcessRequest;
import ma.zakaria.tadbirbudget.workflow.dto.TaskDto;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.Process;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.bpmn.model.StartEvent;
import org.flowable.bpmn.model.UserTask;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.identitylink.api.IdentityLinkType;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import ma.zakaria.tadbirbudget.workflow.dto.ProcessStepDto;
import ma.zakaria.tadbirbudget.workflow.dto.StepperNode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Drive running processes through Flowable: start an instance, read a user's task inbox,
 * claim / release a task (reservation), and complete it (forward or send-back via the
 * task variables). Users are identified by their {@code email}; candidate groups are the
 * caller's role authorities (e.g. {@code ROLE_INSTRUCTOR}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowRuntimeService {

    private final RuntimeService    runtimeService;
    private final TaskService       taskService;
    private final HistoryService    historyService;
    private final RepositoryService repositoryService;
    private final UserRepository    userRepository;

    /** Start a process by its BPMN key; records the starter as the {@code initiator} variable. */
    @Transactional
    public ProcessInstanceDto start(StartProcessRequest request, String starterUserId) {
        Map<String, Object> vars = new HashMap<>(request.variables() == null ? Map.of() : request.variables());
        vars.put("initiator", starterUserId);
        try {
            ProcessInstance pi = runtimeService.startProcessInstanceByKey(
                    request.processKey(), request.businessKey(), vars);
            log.info("Started process key={} businessKey={} instance={}",
                    request.processKey(), request.businessKey(), pi.getId());
            return ProcessInstanceDto.from(pi);
        } catch (FlowableObjectNotFoundException ex) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
    }

    /** Tasks awaiting the user: ones assigned to them + unclaimed ones their roles can claim. */
    @Transactional(readOnly = true)
    public List<TaskDto> inbox(String userId, Collection<String> groups) {
        Map<String, TaskDto> byId = new LinkedHashMap<>();
        taskService.createTaskQuery().taskAssignee(userId)
                .orderByTaskCreateTime().desc().list()
                .forEach(t -> byId.put(t.getId(), enrich(t)));
        if (groups != null && !groups.isEmpty()) {
            taskService.createTaskQuery().taskCandidateGroupIn(new ArrayList<>(groups)).taskUnassigned()
                    .orderByTaskCreateTime().desc().list()
                    .forEach(t -> byId.putIfAbsent(t.getId(), enrich(t)));
        }
        return new ArrayList<>(byId.values());
    }

    /**
     * Reserve a task for the user. Generic candidate-group enforcement: the caller may claim only
     * if one of their roles is a candidate group of the task (as declared by the BPMN
     * {@code candidateGroups}). For the instruction task that group is {@code ROLE_INSTRUCTOR}, so
     * only an instructor can claim it — with zero task names hardcoded here. Fails if already
     * claimed by someone else.
     */
    @Transactional
    public void claim(String taskId, String userId, Collection<String> callerRoles) {
        loadTask(taskId);
        ensureCandidate(taskId, callerRoles);
        try {
            taskService.claim(taskId, userId);
        } catch (FlowableException ex) {
            throw new CustomException(ErrorCode.INVALID_WORKFLOW_STATE, HttpStatus.CONFLICT);
        }
    }

    /** The caller's roles must intersect the task's candidate groups (when it declares any). */
    private void ensureCandidate(String taskId, Collection<String> callerRoles) {
        List<String> candidateGroups = taskCandidateGroups(taskId);
        if (candidateGroups.isEmpty()) {
            return; // task defines no candidate group → no group restriction to enforce
        }
        boolean allowed = callerRoles != null && callerRoles.stream().anyMatch(candidateGroups::contains);
        if (!allowed) {
            throw new CustomException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN);
        }
    }

    /** Candidate-group authorities declared on the task (e.g. ROLE_INSTRUCTOR). */
    private List<String> taskCandidateGroups(String taskId) {
        return taskService.getIdentityLinksForTask(taskId).stream()
                .filter(l -> IdentityLinkType.CANDIDATE.equals(l.getType()) && l.getGroupId() != null)
                .map(IdentityLink::getGroupId)
                .toList();
    }

    /**
     * Release a claimed task back to its candidate pool. Only an <b>instructor</b> may unclaim, and
     * only their own task — a deliberate restriction (validators/commission don't release; admins
     * use {@link #reassign(String, UUID)} instead).
     */
    @Transactional
    public void unclaim(String taskId, String userId, Collection<String> callerRoles) {
        Task task = loadTask(taskId);
        boolean instructor = callerRoles != null && callerRoles.contains(Roles.INSTRUCTOR);
        if (!instructor || !Objects.equals(task.getAssignee(), userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN);
        }
        taskService.unclaim(taskId);
    }

    /**
     * Admin re-assignment: hand a task to another user, allowed only if that user holds one of the
     * task's required roles (its candidate groups) — i.e. "same role". Works whether the task was
     * pooled or already assigned.
     */
    @Transactional
    public void reassign(String taskId, UUID newAssigneeUserId) {
        loadTask(taskId);
        User target = userRepository.findById(newAssigneeUserId)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND));
        List<String> candidateGroups = taskCandidateGroups(taskId);
        if (!candidateGroups.isEmpty() && target.getRoles().stream().noneMatch(candidateGroups::contains)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN); // wrong role for this task
        }
        taskService.setAssignee(taskId, target.getEmail());
    }

    /** Complete a task; variables decide routing (e.g. {@code outcome=VALIDATE|RETURN}). */
    @Transactional
    public void complete(String taskId, CompleteTaskRequest request, String userId) {
        Task task = loadTask(taskId);
        if (task.getAssignee() != null && !task.getAssignee().equals(userId)) {
            throw new CustomException(ErrorCode.ACCESS_DENIED, HttpStatus.FORBIDDEN); // reserved by someone else
        }
        taskService.complete(taskId, request.variables() == null ? Map.of() : request.variables());
    }

    /** Running instances for a business key (e.g. all processes on a pa_request). */
    @Transactional(readOnly = true)
    public List<ProcessInstanceDto> byBusinessKey(String businessKey) {
        return runtimeService.createProcessInstanceQuery().processInstanceBusinessKey(businessKey).list()
                .stream().map(ProcessInstanceDto::from).toList();
    }

    /**
     * The single active task on the instance identified by {@code businessKey}, if any.
     * Domain code uses this to enforce reservation ("are you the assignee?") without naming
     * any BPMN task. Returns empty if there is no running instance or no active task.
     */
    @Transactional(readOnly = true)
    public java.util.Optional<TaskDto> findActiveTask(String businessKey) {
        Task task = taskService.createTaskQuery()
                .processInstanceBusinessKey(businessKey)
                .orderByTaskCreateTime().desc()
                .list().stream().findFirst().orElse(null);
        return java.util.Optional.ofNullable(task).map(this::enrich);
    }

    /**
     * Active task per business key, for list enrichment (one query for a page of records).
     * Returns a map keyed by businessKey; absent keys have no running task.
     */
    @Transactional(readOnly = true)
    public Map<String, TaskDto> findActiveTasks(Collection<String> businessKeys) {
        if (businessKeys == null || businessKeys.isEmpty()) {
            return Map.of();
        }
        Map<String, TaskDto> byKey = new LinkedHashMap<>();
        for (String businessKey : businessKeys) {
            findActiveTask(businessKey).ifPresent(t -> byKey.put(businessKey, t));
        }
        return byKey;
    }

    /**
     * The ordered step (user-task) history of the process for a business key, including completed
     * and active steps. A rework loop shows the same step a second time with a later start time —
     * ideal for a workflow timeline. Reads Flowable history, so it works after the process ends.
     */
    @Transactional(readOnly = true)
    public List<ProcessStepDto> processStepHistory(String businessKey) {
        HistoricProcessInstance pi = historyService.createHistoricProcessInstanceQuery()
                .processInstanceBusinessKey(businessKey)
                .orderByProcessInstanceStartTime().desc()
                .list().stream().findFirst().orElse(null);
        if (pi == null) {
            return List.of();
        }
        return historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(pi.getId())
                .orderByTaskCreateTime().asc()
                .list().stream()
                .map(ProcessStepDto::from)
                .toList();
    }

    /**
     * Business keys of the records whose <b>active</b> task matches the given filters (any null
     * filter is ignored): {@code assignee} (e.g. "assigned to me") and/or {@code taskDefinitionKey}
     * (the current step, e.g. {@code instruction}). Domain code intersects this with its own query.
     */
    @Transactional(readOnly = true)
    public Set<String> activeTaskBusinessKeys(String assignee, String taskDefinitionKey) {
        TaskQuery query = taskService.createTaskQuery();
        if (assignee != null && !assignee.isBlank()) {
            query.taskAssignee(assignee);
        }
        if (taskDefinitionKey != null && !taskDefinitionKey.isBlank()) {
            query.taskDefinitionKey(taskDefinitionKey);
        }
        Set<String> processInstanceIds = query.list().stream()
                .map(Task::getProcessInstanceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (processInstanceIds.isEmpty()) {
            return Set.of();
        }
        return runtimeService.createProcessInstanceQuery()
                .processInstanceIds(processInstanceIds)
                .list().stream()
                .map(ProcessInstance::getBusinessKey)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /**
     * The computed stepper for a record's process: the distinct phases (user tasks) in definition
     * order, de-duplicated, each marked DONE / CURRENT / UPCOMING with its completion instant.
     *
     * <p>The order is read from the BPMN definition (the single source of truth for "what comes
     * next"); the state is derived from where the instance currently is. Everything before the
     * active phase is DONE, the active phase is CURRENT, everything after is UPCOMING — so a phase
     * that ran but was sent back is correctly UPCOMING again (not DONE) and carries no date until
     * it is truly past. When the process has ended, every phase is DONE.
     */
    @Transactional(readOnly = true)
    public List<StepperNode> stepper(String businessKey, String fallbackProcessKey) {
        HistoricProcessInstance pi = historyService.createHistoricProcessInstanceQuery()
                .processInstanceBusinessKey(businessKey)
                .orderByProcessInstanceStartTime().desc()
                .list().stream().findFirst().orElse(null);

        // Not started yet (e.g. NON_DEMARRE): show every phase of the latest definition as UPCOMING.
        if (pi == null) {
            String definitionId = latestDefinitionId(fallbackProcessKey);
            if (definitionId == null) {
                return List.of();
            }
            return orderedUserTaskKeys(definitionId).stream()
                    .map(StepperNode::upcoming)
                    .toList();
        }

        List<String> orderedKeys = orderedUserTaskKeys(pi.getProcessDefinitionId());

        // Most recent completion instant per phase (a phase may have run several rounds).
        Map<String, Instant> lastDoneAt = new HashMap<>();
        for (HistoricTaskInstance t : historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(pi.getId()).finished().list()) {
            if (t.getEndTime() == null) {
                continue;
            }
            lastDoneAt.merge(t.getTaskDefinitionKey(), t.getEndTime().toInstant(),
                    (a, b) -> b.isAfter(a) ? b : a);
        }

        // The phase the instance is currently at (null once the process has ended).
        String currentKey = taskService.createTaskQuery()
                .processInstanceId(pi.getId())
                .list().stream()
                .map(Task::getTaskDefinitionKey)
                .filter(orderedKeys::contains)
                .min(java.util.Comparator.comparingInt(orderedKeys::indexOf))
                .orElse(null);
        int currentIndex = currentKey == null ? -1 : orderedKeys.indexOf(currentKey);

        List<StepperNode> stepper = new ArrayList<>(orderedKeys.size());
        for (int i = 0; i < orderedKeys.size(); i++) {
            String key = orderedKeys.get(i);
            if (i == currentIndex) {
                stepper.add(StepperNode.current(key));
            } else if (currentIndex == -1 || i < currentIndex) {
                stepper.add(StepperNode.done(key, lastDoneAt.get(key)));
            } else {
                stepper.add(StepperNode.upcoming(key));
            }
        }
        return stepper;
    }

    /** Latest deployed version's id for a process key, or null if the key isn't deployed. */
    private String latestDefinitionId(String processKey) {
        if (processKey == null || processKey.isBlank()) {
            return null;
        }
        var definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processKey).latestVersion().singleResult();
        return definition == null ? null : definition.getId();
    }

    /** User-task definition keys in flow order, de-duplicated, by walking the BPMN from the start. */
    private List<String> orderedUserTaskKeys(String processDefinitionId) {
        BpmnModel model = repositoryService.getBpmnModel(processDefinitionId);
        Process process = model.getMainProcess();

        String startId = process.findFlowElementsOfType(StartEvent.class).stream()
                .map(FlowElement::getId).findFirst().orElse(null);
        List<String> ordered = new ArrayList<>();
        if (startId == null) {
            return ordered;
        }
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(startId);
        while (!queue.isEmpty()) {
            String id = queue.poll();
            if (!visited.add(id)) {
                continue;
            }
            FlowElement element = process.getFlowElement(id);
            if (element instanceof UserTask userTask && !ordered.contains(userTask.getId())) {
                ordered.add(userTask.getId());
            }
            if (element instanceof FlowNode node) {
                for (SequenceFlow flow : node.getOutgoingFlows()) {
                    queue.add(flow.getTargetRef());
                }
            }
        }
        return ordered;
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Task loadTask(String taskId) {
        Task task = taskService.createTaskQuery().taskId(taskId).singleResult();
        if (task == null) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }
        return task;
    }

    private TaskDto enrich(Task task) {
        ProcessInstance pi = runtimeService.createProcessInstanceQuery()
                .processInstanceId(task.getProcessInstanceId()).singleResult();
        return TaskDto.from(task).withBusinessKey(pi == null ? null : pi.getBusinessKey());
    }
}
