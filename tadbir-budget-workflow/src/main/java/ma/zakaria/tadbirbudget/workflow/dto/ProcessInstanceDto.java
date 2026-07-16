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

import org.flowable.engine.runtime.ProcessInstance;

/** A running process instance. */
public record ProcessInstanceDto(
        String id,
        String processDefinitionKey,
        String businessKey,
        boolean ended,
        boolean suspended) {

    public static ProcessInstanceDto from(ProcessInstance p) {
        return new ProcessInstanceDto(p.getId(), p.getProcessDefinitionKey(),
                p.getBusinessKey(), p.isEnded(), p.isSuspended());
    }
}
