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
package ma.zakaria.tadbirbudget.workflow.listener;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;

/**
 * Reusable, generic "stick with the same person across a loop" listener. Register it on a task
 * for both the {@code create} and {@code complete} events:
 *
 * <pre>{@code
 * <userTask id="review" flowable:candidateGroups="ROLE_EMPLOYEE">
 *   <extensionElements>
 *     <flowable:taskListener event="create"     delegateExpression="${completerCaptureListener}"/>
 *     <flowable:taskListener event="assignment" delegateExpression="${completerCaptureListener}"/>
 *   </extensionElements>
 * </userTask>
 * }</pre>
 *
 * <p>On <b>assignment</b> (i.e. when someone claims or is given the task) it remembers that
 * person's uid in the process variable {@code <taskDefinitionKey>Assignee}. On <b>create</b>,
 * if that variable already holds someone (the flow looped back here), it reserves the task for
 * that same person; otherwise it leaves the task unassigned so its candidate-group pool applies.
 *
 * <p>Capturing on {@code assignment} rather than {@code complete} is deliberate: the variable is
 * written while the task is in a stable state, which is more reliable than writing during the
 * task's completion transition. It also avoids a {@code flowable:assignee="${...}"} expression,
 * which throws "Unknown property" on the first pass before the variable exists. The variable name
 * is derived from the task key, so it works for any looping task with no per-workflow code.
 */
@Slf4j
@Component("completerCaptureListener")
public class CompleterCaptureListener implements TaskListener {

    /** Suffix appended to the task definition key to form the process-variable name. */
    public static final String ASSIGNEE_VARIABLE_SUFFIX = "Assignee";

    @Override
    public void notify(DelegateTask delegateTask) {
        String variableName = delegateTask.getTaskDefinitionKey() + ASSIGNEE_VARIABLE_SUFFIX;

        if (EVENTNAME_CREATE.equals(delegateTask.getEventName())) {
            Object remembered = delegateTask.getVariable(variableName);
            if (remembered instanceof String email && !email.isBlank() && delegateTask.getAssignee() == null) {
                delegateTask.setAssignee(email);
                log.debug("Loop re-entry: reserved task {} for previous assignee {}",
                        delegateTask.getTaskDefinitionKey(), email);
            }
        } else if (EVENTNAME_ASSIGNMENT.equals(delegateTask.getEventName())) {
            String assignee = delegateTask.getAssignee();
            if (assignee != null) {
                delegateTask.setVariable(variableName, assignee);
                log.debug("Captured assignee {} into process variable {}", assignee, variableName);
            }
        }
    }
}
