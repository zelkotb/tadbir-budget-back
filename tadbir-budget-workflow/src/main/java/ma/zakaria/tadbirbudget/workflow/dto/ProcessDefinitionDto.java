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

import org.flowable.engine.repository.ProcessDefinition;

/** A deployed BPMN process definition (one row per version). */
public record ProcessDefinitionDto(
        String id,
        String key,
        String name,
        int version,
        String deploymentId,
        boolean suspended) {

    public static ProcessDefinitionDto from(ProcessDefinition d) {
        return new ProcessDefinitionDto(d.getId(), d.getKey(), d.getName(),
                d.getVersion(), d.getDeploymentId(), d.isSuspended());
    }
}
