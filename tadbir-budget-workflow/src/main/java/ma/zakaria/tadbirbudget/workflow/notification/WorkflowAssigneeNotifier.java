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
package ma.zakaria.tadbirbudget.workflow.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.zakaria.tadbirbudget.entity.User;
import ma.zakaria.tadbirbudget.entity.enums.NotificationChannel;
import ma.zakaria.tadbirbudget.notification.api.NotificationRequest;
import ma.zakaria.tadbirbudget.notification.api.NotificationService;
import ma.zakaria.tadbirbudget.notification.template.NotificationTemplate;
import ma.zakaria.tadbirbudget.repository.UserRepository;
import ma.zakaria.tadbirbudget.workflow.event.WorkflowTaskCreatedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;
import java.util.Set;

/**
 * Generic, domain-agnostic notification: when a task is reserved for a specific person, tell
 * that person a dossier awaits their action. Works for every task in every BPMN — it never
 * references a task name or a role map.
 *
 * <p><b>Policy:</b> only the task's {@code assignee} is notified. Unassigned pool tasks are
 * <em>not</em> broadcast to a whole role — candidates discover those in their own work list.
 * To proactively notify someone for a step, model the BPMN to assign it to a concrete person
 * (e.g. {@code flowable:assignee="${manager}"} for the N+1).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowAssigneeNotifier {

    @Value("${workflow.notifications.enabled:true}")
    private boolean enabled;

    private final UserRepository      userRepository;
    private final NotificationService notificationService;

    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTaskCreated(WorkflowTaskCreatedEvent event) {
        if (!enabled || event.getAssignee() == null) {
            return; // pool task (no assignee) → nobody is notified by design
        }
        try {
            userRepository.findByUid(event.getAssignee()).ifPresent(user -> notificationService.enqueue(
                    NotificationRequest.of(
                            user.getId(), user.getEmail(),
                            Set.of(NotificationChannel.MAIL, NotificationChannel.APPLICATION),
                            NotificationTemplate.WORKFLOW_STEP_ASSIGNED,
                            Map.of(
                                    "recipientName", recipientName(user),
                                    "requestNumber", requestLabel(event),
                                    "stepName", nullToEmpty(event.getTaskName())),
                            event.getBusinessKey())));
        } catch (Exception e) {
            log.warn("Assignee notification failed for task={} assignee={}",
                    event.getTaskId(), event.getAssignee(), e);
        }
    }

    private String recipientName(User user) {
        return user.getFullName() != null ? user.getFullName() : user.getEmail();
    }

    /** Human-readable label (request number) with a fallback to the raw business key. */
    private String requestLabel(WorkflowTaskCreatedEvent event) {
        return event.getBusinessLabel() != null ? event.getBusinessLabel() : nullToEmpty(event.getBusinessKey());
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
