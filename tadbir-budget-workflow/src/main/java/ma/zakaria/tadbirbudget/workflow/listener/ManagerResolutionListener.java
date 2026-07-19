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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.zakaria.tadbirbudget.entity.User;
import ma.zakaria.tadbirbudget.repository.UserRepository;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.stereotype.Component;

/**
 * Reusable, generic N+1 resolver. Attach it to <em>any</em> task whose completion should hand
 * the next step to the completer's hierarchical superior:
 *
 * <pre>{@code
 * <userTask id="review" flowable:candidateGroups="ROLE_EMPLOYEE">
 *   <extensionElements>
 *     <flowable:taskListener event="complete" delegateExpression="${managerResolutionListener}"/>
 *   </extensionElements>
 * </userTask>
 * <userTask id="managerReview" flowable:assignee="${manager}"
 *           flowable:candidateGroups="ROLE_DEPARTMENT_MANAGER"/>
 * }</pre>
 *
 * On completion it sets the process variable {@code manager} to the uid of the completer's
 * {@code manager_id} (their N+1). The next task can then bind {@code flowable:assignee="${manager}"}.
 * If the completer has no manager, {@code manager} is left null and the next task falls back to
 * its candidate group. No workflow-specific code — works for any process that opts in.
 */
@Slf4j
@Component("managerResolutionListener")
@RequiredArgsConstructor
public class ManagerResolutionListener implements TaskListener {

    /** The process variable populated with the N+1's uid. */
    public static final String MANAGER_VARIABLE = "manager";

    private final transient UserRepository userRepository;

    @Override
    public void notify(DelegateTask delegateTask) {
        String completerUid = delegateTask.getAssignee();
        if (completerUid == null) {
            log.warn("ManagerResolutionListener: task {} completed without an assignee — cannot resolve N+1",
                    delegateTask.getId());
            delegateTask.setVariable(MANAGER_VARIABLE, null);
            return;
        }
        String managerUid = userRepository.findByUid(completerUid)
                .map(User::getManagerId)
                .flatMap(userRepository::findById)
                .map(User::getUid)
                .orElse(null);

        if (managerUid == null) {
            log.warn("No manager (N+1) resolved for {} — next task will fall back to its candidate group",
                    completerUid);
        }
        delegateTask.setVariable(MANAGER_VARIABLE, managerUid);
    }
}
