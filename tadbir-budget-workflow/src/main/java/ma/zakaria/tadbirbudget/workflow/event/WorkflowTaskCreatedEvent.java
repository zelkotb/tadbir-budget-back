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

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * Domain-agnostic event published after a Flowable user task is created (on process start,
 * advance, or after a send-back). Carries everything a consumer needs without anyone having
 * to query Flowable or know the BPMN structure.
 *
 * <ul>
 *   <li>The <b>workflow</b> module uses it to notify the task's assignee.</li>
 *   <li>A <b>domain</b> module (e.g. request) uses it to notify the record's owner that
 *       their dossier reached a new step — it maps {@link #businessKey} to its own record.</li>
 * </ul>
 */
@Getter
public class WorkflowTaskCreatedEvent extends ApplicationEvent {

    private final String processKey;
    private final String businessKey;
    /**
     * Human-readable label for the business record (e.g. a request number), taken from the
     * {@code businessLabel} process variable. Null if the process did not set one — consumers
     * should fall back to {@link #businessKey}. The workflow module stays oblivious to what it
     * means; the starting domain decides the label.
     */
    private final String businessLabel;
    private final String taskId;
    private final String taskName;
    private final String taskDefinitionKey;
    /** Email of the user the task is reserved for, or null for an unclaimed pool task. */
    private final String assignee;
    /** Candidate group authorities (roles) that may claim the task. */
    private final List<String> candidateGroups;

    public WorkflowTaskCreatedEvent(Object source, String processKey, String businessKey,
                                    String businessLabel, String taskId, String taskName,
                                    String taskDefinitionKey, String assignee,
                                    List<String> candidateGroups) {
        super(source);
        this.processKey = processKey;
        this.businessKey = businessKey;
        this.businessLabel = businessLabel;
        this.taskId = taskId;
        this.taskName = taskName;
        this.taskDefinitionKey = taskDefinitionKey;
        this.assignee = assignee;
        this.candidateGroups = candidateGroups;
    }
}
