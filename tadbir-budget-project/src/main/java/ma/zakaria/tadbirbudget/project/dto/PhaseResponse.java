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
package ma.zakaria.tadbirbudget.project.dto;

import ma.zakaria.tadbirbudget.entity.ProjectPhase;
import ma.zakaria.tadbirbudget.entity.enums.PhaseStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A project phase. The front computes project KPIs from the list of these. */
public record PhaseResponse(
        UUID        id,
        UUID        projectId,
        String      title,
        String      description,
        PhaseStatus status,
        BigDecimal  weight,
        BigDecimal  completion,
        LocalDate   startDate,
        LocalDate   endDate,
        LocalDate   firstStartDate,
        LocalDate   firstEndDate,
        String      createdBy,
        Instant     createdAt
) {
    public static PhaseResponse from(ProjectPhase p) {
        return new PhaseResponse(
                p.getId(), p.getProjectId(), p.getTitle(), p.getDescription(), p.getStatus(),
                p.getWeight(), p.getCompletion(), p.getStartDate(), p.getEndDate(),
                p.getFirstStartDate(), p.getFirstEndDate(), p.getCreatedBy(), p.getCreatedAt());
    }
}
