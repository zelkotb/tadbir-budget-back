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

import org.flowable.task.api.Task;

import java.util.Date;

/**
 * One human task in an inbox. {@code assignee} null = unclaimed (claimable by a candidate);
 * non-null = reserved by that user.
 */
public record TaskDto(
        String id,
        String name,
        String processInstanceId,
        String processDefinitionId,
        String businessKey,
        String assignee,
        Date createTime,
        Date dueDate) {

    public static TaskDto from(Task t) {
        return new TaskDto(t.getId(), t.getName(), t.getProcessInstanceId(),
                t.getProcessDefinitionId(), null, t.getAssignee(), t.getCreateTime(), t.getDueDate());
    }

    /** Same task but carrying the resolved business key (Task does not expose it directly). */
    public TaskDto withBusinessKey(String businessKey) {
        return new TaskDto(id, name, processInstanceId, processDefinitionId, businessKey,
                assignee, createTime, dueDate);
    }
}
