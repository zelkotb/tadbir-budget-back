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

import java.util.Map;

/**
 * Complete a task. The variables drive what happens next — e.g. an {@code outcome}
 * variable read by an exclusive gateway to route forward or send back
 * (e.g. {@code {"outcome":"RETURN","observation":"..."}}).
 */
public record CompleteTaskRequest(Map<String, Object> variables) {
}
