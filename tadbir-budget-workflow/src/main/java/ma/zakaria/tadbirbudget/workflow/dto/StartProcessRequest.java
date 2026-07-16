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

import jakarta.validation.constraints.NotBlank;

import java.util.Map;

/**
 * Start a process instance.
 *
 * @param processKey  BPMN process id (the {@code id} of the {@code <process>} element)
 * @param businessKey opaque pointer to the domain record (e.g. a pa_request id)
 * @param variables   initial process variables (used by gateways/conditions)
 */
public record StartProcessRequest(
        @NotBlank String processKey,
        String businessKey,
        Map<String, Object> variables) {
}
