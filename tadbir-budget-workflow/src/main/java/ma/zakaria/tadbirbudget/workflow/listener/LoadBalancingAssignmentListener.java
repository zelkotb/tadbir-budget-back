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
import org.flowable.engine.TaskService;
import org.flowable.engine.delegate.TaskListener;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.identitylink.api.IdentityLinkType;
import org.flowable.task.service.delegate.DelegateTask;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Reusable, generic load-balancing assignment. Attach it to a pool task's {@code create} event,
 * <b>after</b> {@code ${completerCaptureListener}}: if the task is still unassigned, it picks the
 * <b>least-loaded</b> enabled user holding one of the task's candidate-group roles (load = number
 * of tasks currently assigned to that user) and also records that choice as the task's sticky
 * assignee variable — so if the flow loops back here later, {@code completerCaptureListener}
 * re-assigns the <b>same</b> person ("whoever starts finishes", unless an admin reassigns). If the
 * task is already assigned, or nobody holds the role, it does nothing. No task names or roles are
 * hardcoded — the role comes from the BPMN candidate groups, so the same bean serves any pool step.
 */
@Slf4j
@Component("loadBalancingAssignmentListener")
@RequiredArgsConstructor
public class LoadBalancingAssignmentListener implements TaskListener {

    private final UserRepository userRepository;
    // Lazy: the engine builds this bean while it is still starting (via the BPMN delegate).
    private final ObjectProvider<TaskService> taskServiceProvider;

    @Override
    public void notify(DelegateTask task) {
        if (task.getAssignee() != null) {
            return; // already assigned (e.g. a returned task) — leave it
        }
        TaskService taskService = taskServiceProvider.getObject();
        List<String> roles = taskService.getIdentityLinksForTask(task.getId()).stream()
                .filter(l -> IdentityLinkType.CANDIDATE.equals(l.getType()) && l.getGroupId() != null)
                .map(IdentityLink::getGroupId)
                .distinct()
                .toList();
        if (roles.isEmpty()) {
            return;
        }

        Map<UUID, User> candidates = new LinkedHashMap<>();
        for (String role : roles) {
            for (User user : userRepository.findByRoleContaining(role)) {
                candidates.putIfAbsent(user.getId(), user);
            }
        }
        if (candidates.isEmpty()) {
            return; // no eligible user → stays in the pool for manual claim
        }

        User chosen = candidates.values().stream()
                .min(Comparator.comparingLong(u -> taskService.createTaskQuery()
                        .taskAssignee(u.getEmail()).count()))
                .orElseThrow();
        task.setAssignee(chosen.getEmail());
        // Remember the choice so a loop back to this step re-assigns the same person.
        task.setVariable(task.getTaskDefinitionKey() + CompleterCaptureListener.ASSIGNEE_VARIABLE_SUFFIX,
                chosen.getEmail());
        log.debug("Load-balanced task {} to {}", task.getTaskDefinitionKey(), chosen.getEmail());
    }
}
