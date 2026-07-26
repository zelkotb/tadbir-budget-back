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
package ma.zakaria.tadbirbudget.project.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import ma.zakaria.tadbirbudget.entity.Project;
import ma.zakaria.tadbirbudget.entity.RevInfo;
import ma.zakaria.tadbirbudget.enums.AuditAction;
import ma.zakaria.tadbirbudget.exception.CustomException;
import ma.zakaria.tadbirbudget.exception.ErrorCode;
import ma.zakaria.tadbirbudget.project.dto.ProjectAuditDiffResponse;
import ma.zakaria.tadbirbudget.project.dto.ProjectAuditResponse;
import org.hibernate.envers.AuditReader;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.hibernate.envers.query.AuditQuery;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * Read-only view over the {@link Project} Envers history — the same approach as
 * {@code UserAuditService}: a paginated/filterable log plus a field-level diff per revision.
 */
@Service
@RequiredArgsConstructor
public class ProjectAuditService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EntityManager entityManager;

    /**
     * Returns a paginated, filterable view of the Project audit log.
     *
     * @param performedBy partial uid of the actor who made the change (LIKE)
     * @param ip          partial IP address filter (LIKE)
     * @param action      CREATE / UPDATE / DELETE
     * @param projectId   exact project id whose history to retrieve
     * @param date        date in DD/MM/YYYY format — filters by that specific day
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Page<ProjectAuditResponse> query(String performedBy, String ip,
                                            AuditAction action, UUID projectId,
                                            String date, Pageable pageable) {
        AuditReader reader = AuditReaderFactory.get(entityManager);

        AuditQuery dataQuery  = buildQuery(reader, performedBy, ip, action, projectId, date);
        AuditQuery countQuery = buildQuery(reader, performedBy, ip, action, projectId, date);

        dataQuery
                .addOrder(AuditEntity.revisionProperty("timestamp").desc())
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize());

        countQuery.addProjection(AuditEntity.id().count());

        long total = ((Number) countQuery.getSingleResult()).longValue();

        List<Object[]> rows = (List<Object[]>) dataQuery.getResultList();
        List<ProjectAuditResponse> content = rows.stream()
                .map(r -> ProjectAuditResponse.from((Project) r[0], (RevInfo) r[1], (RevisionType) r[2]))
                .toList();

        return new PageImpl<>(content, pageable, total);
    }

    /**
     * Returns the field-level diff for a single audit revision.
     *
     * @param revisionId the revinfo.id — present in every ProjectAuditResponse
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public ProjectAuditDiffResponse getDiff(int revisionId) {
        AuditReader reader = AuditReaderFactory.get(entityManager);

        List<Object[]> rows = reader.createQuery()
                .forRevisionsOfEntity(Project.class, false, true)
                .add(AuditEntity.revisionNumber().eq(revisionId))
                .getResultList();

        if (rows.isEmpty()) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        Object[]     row       = rows.get(0);
        Project      current   = (Project) row[0];
        RevInfo      revInfo   = (RevInfo) row[1];
        RevisionType revType   = (RevisionType) row[2];
        UUID         projectId = current.getId();

        // For UPDATE / DELETE load the revision that immediately preceded this one.
        Project previous = null;
        if (revType != RevisionType.ADD) {
            List<Object[]> prevRows = reader.createQuery()
                    .forRevisionsOfEntity(Project.class, false, true)
                    .add(AuditEntity.id().eq(projectId))
                    .add(AuditEntity.revisionNumber().lt(revisionId))
                    .addOrder(AuditEntity.revisionNumber().desc())
                    .setMaxResults(1)
                    .getResultList();
            if (!prevRows.isEmpty()) {
                previous = (Project) prevRows.get(0)[0];
            }
        }

        // Map revision type to before/after states:
        //   ADD  → before=null,     after=current   (entity created)
        //   MOD  → before=previous, after=current   (entity updated)
        //   DEL  → before=current,  after=null      (entity deleted;
        //           store_data_at_delete=true means current holds the last known state)
        Project beforeState = switch (revType) { case ADD -> null;    case MOD -> previous; default -> current; };
        Project afterState  = switch (revType) { case DEL -> null;    default  -> current; };

        AuditAction action = switch (revType) {
            case ADD -> AuditAction.CREATE;
            case MOD -> AuditAction.UPDATE;
            case DEL -> AuditAction.DELETE;
        };

        return new ProjectAuditDiffResponse(
                revisionId,
                Instant.ofEpochMilli(revInfo.getTimestamp()),
                revInfo.getActor(),
                revInfo.getIp(),
                action,
                projectId,
                current.getName(),
                buildChanges(beforeState, afterState)
        );
    }

    private List<ProjectAuditDiffResponse.FieldChange> buildChanges(Project before, Project after) {
        List<ProjectAuditDiffResponse.FieldChange> changes = new ArrayList<>();
        diff(changes, "name",            before, after, Project::getName);
        diff(changes, "objectifs",       before, after, Project::getObjectifs);
        diff(changes, "description",     before, after, Project::getDescription);
        diff(changes, "status",          before, after, Project::getStatus);
        diff(changes, "chefProjetId",    before, after, Project::getChefProjetId);
        diff(changes, "orgUnitId",       before, after, Project::getOrgUnitId);
        diff(changes, "startDate",       before, after, Project::getStartDate);
        diff(changes, "terminationDate", before, after, Project::getTerminationDate);
        diff(changes, "createdBy",       before, after, Project::getCreatedBy);
        return changes;
    }

    private <T> void diff(List<ProjectAuditDiffResponse.FieldChange> changes, String field,
                          Project before, Project after, Function<Project, T> getter) {
        T b = before != null ? getter.apply(before) : null;
        T a = after  != null ? getter.apply(after)  : null;
        if (!Objects.equals(b, a)) {
            changes.add(new ProjectAuditDiffResponse.FieldChange(field, b, a));
        }
    }

    private AuditQuery buildQuery(AuditReader reader, String performedBy, String ip,
                                  AuditAction action, UUID projectId, String date) {
        AuditQuery q = reader.createQuery()
                .forRevisionsOfEntity(Project.class, false, true);

        if (performedBy != null) {
            q.add(AuditEntity.revisionProperty("actor").like("%" + performedBy + "%"));
        }
        if (ip != null) {
            q.add(AuditEntity.revisionProperty("ip").like("%" + ip + "%"));
        }
        if (action != null) {
            RevisionType revType = switch (action) {
                case CREATE -> RevisionType.ADD;
                case UPDATE -> RevisionType.MOD;
                case DELETE -> RevisionType.DEL;
            };
            q.add(AuditEntity.revisionType().eq(revType));
        }
        if (projectId != null) {
            q.add(AuditEntity.id().eq(projectId));
        }
        if (date != null) {
            LocalDate d     = LocalDate.parse(date, DATE_FMT);
            long      start = d.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            long      end   = d.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            q.add(AuditEntity.revisionProperty("timestamp").ge(start));
            q.add(AuditEntity.revisionProperty("timestamp").lt(end));
        }

        return q;
    }
}
