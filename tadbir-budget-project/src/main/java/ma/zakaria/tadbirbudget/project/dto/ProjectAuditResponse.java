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
import ma.zakaria.tadbirbudget.entity.RevInfo;
import ma.zakaria.tadbirbudget.entity.enums.ProjectStatus;
import ma.zakaria.tadbirbudget.enums.AuditAction;
import org.hibernate.envers.RevisionType;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** One row of the Project Envers audit log — the project's state at a revision + who/when. */
public record ProjectAuditResponse(
        int           revisionId,
        Instant       occurredAt,
        String        performedBy,
        String        performedFrom,
        AuditAction   action,
        UUID          projectId,
        String        name,
        String        objectifs,
        String        description,
        ProjectStatus status,
        UUID          chefProjetId,
        UUID          orgUnitId,
        LocalDate     startDate,
        LocalDate     terminationDate,
        String        createdBy
) {
    public static ProjectAuditResponse from(Project p, RevInfo rev, RevisionType type) {
        AuditAction action = switch (type) {
            case ADD -> AuditAction.CREATE;
            case MOD -> AuditAction.UPDATE;
            case DEL -> AuditAction.DELETE;
        };
        return new ProjectAuditResponse(
                rev.getId(),
                Instant.ofEpochMilli(rev.getTimestamp()),
                rev.getActor(),
                rev.getIp(),
                action,
                p != null ? p.getId()              : null,
                p != null ? p.getName()            : null,
                p != null ? p.getObjectifs()       : null,
                p != null ? p.getDescription()     : null,
                p != null ? p.getStatus()          : null,
                p != null ? p.getChefProjetId()    : null,
                p != null ? p.getOrgUnitId()       : null,
                p != null ? p.getStartDate()       : null,
                p != null ? p.getTerminationDate() : null,
                p != null ? p.getCreatedBy()       : null
        );
    }
}
