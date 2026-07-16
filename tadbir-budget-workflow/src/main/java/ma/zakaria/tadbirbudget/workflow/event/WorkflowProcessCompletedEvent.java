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

import java.util.Map;

/**
 * Domain-agnostic event published when a Flowable process instance reaches an end event.
 * {@link #variables} carries the final process variables (e.g. {@code outcome}) so a domain
 * consumer can react without querying Flowable.
 */
@Getter
public class WorkflowProcessCompletedEvent extends ApplicationEvent {

    private final String processKey;
    private final String businessKey;
    private final Map<String, Object> variables;

    public WorkflowProcessCompletedEvent(Object source, String processKey, String businessKey,
                                         Map<String, Object> variables) {
        super(source);
        this.processKey = processKey;
        this.businessKey = businessKey;
        this.variables = variables;
    }
}
