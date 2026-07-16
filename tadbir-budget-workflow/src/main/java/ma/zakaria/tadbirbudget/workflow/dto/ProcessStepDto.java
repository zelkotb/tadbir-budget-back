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
package ma.zakaria.tadbirbudget.workflow.dto;

import org.flowable.task.api.history.HistoricTaskInstance;

import java.util.Date;

/**
 * One step (user task) in a process instance's history, for rendering a workflow timeline.
 * A rework loop naturally appears as the same {@code activityId} a second time with a later
 * {@code startTime}. {@code endTime} null = the step is still active.
 *
 * <p>Built from {@link HistoricTaskInstance} (not the activity history) so {@code assignee} is
 * always populated — including after a send-back loop, where the activity history can leave it
 * empty.
 *
 * <p>{@code startTime} is the <b>claim</b> time when the task was claimed from a pool (so an
 * instruction step starts when the instructor takes it, not when the citizen submitted), and
 * falls back to the create time for directly-assigned tasks (e.g. the director's validation).
 */
public record ProcessStepDto(
        String activityId,
        String name,
        String assignee,
        Date startTime,
        Date endTime,
        Long durationMillis) {

    public static ProcessStepDto from(HistoricTaskInstance t) {
        Date start = t.getClaimTime() != null ? t.getClaimTime() : t.getCreateTime();
        return new ProcessStepDto(
                t.getTaskDefinitionKey(),
                t.getName(),
                t.getAssignee(),
                start,
                t.getEndTime(),
                t.getDurationInMillis());
    }
}
