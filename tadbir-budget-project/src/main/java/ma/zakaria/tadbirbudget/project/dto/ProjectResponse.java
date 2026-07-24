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

import ma.zakaria.tadbirbudget.entity.Project;
import ma.zakaria.tadbirbudget.entity.enums.ProjectStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** A project. {@code team} is populated on the single-project read and null in list responses. */
public record ProjectResponse(
        UUID                        id,
        String                      name,
        String                      objectifs,
        String                      description,
        ProjectStatus               status,
        UUID                        chefProjetId,
        String                      chefProjetName,
        UUID                        orgUnitId,
        String                      orgUnitName,
        LocalDate                   startDate,
        Integer                     terminationYear,
        String                      createdBy,
        Instant                     createdAt,
        long                        memberCount,
        List<ProjectMemberResponse> team
) {
    public static ProjectResponse from(Project p, String chefProjetName, String orgUnitName,
                                       long memberCount, List<ProjectMemberResponse> team) {
        return new ProjectResponse(
                p.getId(), p.getName(), p.getObjectifs(), p.getDescription(),
                p.getStatus(), p.getChefProjetId(), chefProjetName, p.getOrgUnitId(), orgUnitName,
                p.getStartDate(), p.getTerminationYear(), p.getCreatedBy(), p.getCreatedAt(),
                memberCount, team);
    }
}
