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
package ma.zakaria.tadbirbudget.workflow.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.impl.persistence.entity.ExecutionEntity;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.identitylink.api.IdentityLinkType;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * The single seam between Flowable and the rest of the app. It listens to engine events and
 * republishes them as domain-agnostic Spring {@link org.springframework.context.ApplicationEvent}s,
 * enriched with everything consumers need (business key, process key, candidate groups…).
 *
 * <p>This keeps every other component free of Flowable APIs and lets any module subscribe to
 * workflow progress without touching the workflow code. It never throws
 * ({@link #isFailOnException()} = {@code false}), so a downstream problem can never roll back
 * a workflow transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowEventBridge implements FlowableEventListener {

    private final ApplicationEventPublisher publisher;
    // Lazy: the engine pulls this bean in while it is still being built (via the engine
    // configurer). Resolving the engine services through providers avoids that init cycle.
    private final ObjectProvider<RuntimeService> runtimeServiceProvider;
    private final ObjectProvider<TaskService>    taskServiceProvider;

    @Override
    public void onEvent(FlowableEvent event) {
        try {
            if (FlowableEngineEventType.TASK_CREATED.equals(event.getType())
                    && event instanceof FlowableEngineEntityEvent entityEvent
                    && entityEvent.getEntity() instanceof Task task) {
                onTaskCreated(entityEvent, task);
            } else if (FlowableEngineEventType.PROCESS_COMPLETED.equals(event.getType())
                    && event instanceof FlowableEngineEntityEvent entityEvent
                    && entityEvent.getEntity() instanceof ExecutionEntity execution) {
                onProcessCompleted(execution);
            }
        } catch (Exception e) {
            log.warn("WorkflowEventBridge failed to publish event {}", event.getType(), e);
        }
    }

    private void onTaskCreated(FlowableEngineEntityEvent event, Task task) {
        String processKey  = keyFromDefinitionId(event.getProcessDefinitionId());
        String businessKey = businessKeyOf(event.getProcessInstanceId());
        String businessLabel = businessLabelOf(event.getProcessInstanceId());
        List<String> candidateGroups = taskServiceProvider.getObject().getIdentityLinksForTask(task.getId()).stream()
                .filter(l -> IdentityLinkType.CANDIDATE.equals(l.getType()) && l.getGroupId() != null)
                .map(IdentityLink::getGroupId)
                .toList();

        publisher.publishEvent(new WorkflowTaskCreatedEvent(
                this, processKey, businessKey, businessLabel, task.getId(), task.getName(),
                task.getTaskDefinitionKey(), task.getAssignee(), candidateGroups));
    }

    private void onProcessCompleted(ExecutionEntity execution) {
        String processKey  = keyFromDefinitionId(execution.getProcessDefinitionId());
        String businessKey = execution.getProcessInstanceBusinessKey();
        Map<String, Object> variables = execution.getVariables();

        publisher.publishEvent(new WorkflowProcessCompletedEvent(
                this, processKey, businessKey, variables));
    }

    private String businessKeyOf(String processInstanceId) {
        if (processInstanceId == null) return null;
        ProcessInstance pi = runtimeServiceProvider.getObject().createProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult();
        return pi != null ? pi.getBusinessKey() : null;
    }

    /** Generic human-readable label set by the starting domain (e.g. the request number). */
    private String businessLabelOf(String processInstanceId) {
        if (processInstanceId == null) return null;
        Object label = runtimeServiceProvider.getObject().getVariable(processInstanceId, "businessLabel");
        return label == null ? null : label.toString();
    }

    /** Flowable definition ids look like "KEY:version:deploymentPart" — the key is the prefix. */
    private String keyFromDefinitionId(String processDefinitionId) {
        if (processDefinitionId == null) return null;
        int colon = processDefinitionId.indexOf(':');
        return colon > 0 ? processDefinitionId.substring(0, colon) : processDefinitionId;
    }

    @Override
    public boolean isFailOnException() {
        return false;
    }

    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        return false;
    }

    @Override
    public String getOnTransaction() {
        return null;
    }
}
