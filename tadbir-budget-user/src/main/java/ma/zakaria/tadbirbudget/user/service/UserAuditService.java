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
package ma.zakaria.tadbirbudget.user.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import ma.zakaria.tadbirbudget.entity.RevInfo;
import ma.zakaria.tadbirbudget.entity.User;
import ma.zakaria.tadbirbudget.enums.AuditAction;
import ma.zakaria.tadbirbudget.exception.CustomException;
import ma.zakaria.tadbirbudget.exception.ErrorCode;
import ma.zakaria.tadbirbudget.user.dto.UserAuditDiffResponse;
import ma.zakaria.tadbirbudget.user.dto.UserAuditResponse;
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

@Service
@RequiredArgsConstructor
public class UserAuditService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final EntityManager entityManager;

    /**
     * Returns a paginated, filterable view of the User audit log.
     *
     * @param performedBy partial email of the admin who made the change (LIKE)
     * @param ip          partial IP address filter (LIKE)
     * @param action      CREATE / UPDATE / DELETE
     * @param userId      exact user ID whose history to retrieve
     * @param date        date in DD/MM/YYYY format — filters by that specific day
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public Page<UserAuditResponse> query(String performedBy, String ip,
                                         AuditAction action, UUID userId,
                                         String date, Pageable pageable) {
        AuditReader reader = AuditReaderFactory.get(entityManager);

        AuditQuery dataQuery  = buildQuery(reader, performedBy, ip, action, userId, date);
        AuditQuery countQuery = buildQuery(reader, performedBy, ip, action, userId, date);

        dataQuery
                .addOrder(AuditEntity.revisionProperty("timestamp").desc())
                .setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize());

        countQuery.addProjection(AuditEntity.id().count());

        long total = ((Number) countQuery.getSingleResult()).longValue();

        List<Object[]> rows = (List<Object[]>) dataQuery.getResultList();
        List<UserAuditResponse> content = rows.stream()
                .map(r -> UserAuditResponse.from((User) r[0], (RevInfo) r[1], (RevisionType) r[2]))
                .toList();

        return new PageImpl<>(content, pageable, total);
    }

    /**
     * Returns the field-level diff for a single audit revision.
     *
     * @param revisionId the revinfo.id — present in every UserAuditResponse
     */
    @Transactional(readOnly = true)
    @SuppressWarnings("unchecked")
    public UserAuditDiffResponse getDiff(int revisionId) {
        AuditReader reader = AuditReaderFactory.get(entityManager);

        List<Object[]> rows = reader.createQuery()
                .forRevisionsOfEntity(User.class, false, true)
                .add(AuditEntity.revisionNumber().eq(revisionId))
                .getResultList();

        if (rows.isEmpty()) {
            throw new CustomException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND);
        }

        Object[]     row     = rows.get(0);
        User         current = (User) row[0];
        RevInfo      revInfo = (RevInfo) row[1];
        RevisionType revType = (RevisionType) row[2];
        UUID         userId  = current.getId();

        // For UPDATE / DELETE load the revision that immediately preceded this one.
        User previous = null;
        if (revType != RevisionType.ADD) {
            List<Object[]> prevRows = reader.createQuery()
                    .forRevisionsOfEntity(User.class, false, true)
                    .add(AuditEntity.id().eq(userId))
                    .add(AuditEntity.revisionNumber().lt(revisionId))
                    .addOrder(AuditEntity.revisionNumber().desc())
                    .setMaxResults(1)
                    .getResultList();
            if (!prevRows.isEmpty()) {
                previous = (User) prevRows.get(0)[0];
            }
        }

        // Map revision type to before/after states:
        //   ADD  → before=null,    after=current   (entity created)
        //   MOD  → before=previous, after=current  (entity updated)
        //   DEL  → before=current, after=null      (entity deleted;
        //           store_data_at_delete=true means current holds the last known state)
        User beforeState = switch (revType) { case ADD -> null;    case MOD -> previous; default -> current; };
        User afterState  = switch (revType) { case DEL -> null;    default  -> current; };

        AuditAction action = switch (revType) {
            case ADD -> AuditAction.CREATE;
            case MOD -> AuditAction.UPDATE;
            case DEL -> AuditAction.DELETE;
        };

        return new UserAuditDiffResponse(
                revisionId,
                Instant.ofEpochMilli(revInfo.getTimestamp()),
                revInfo.getEmail(),
                revInfo.getIp(),
                action,
                userId,
                current.getEmail(),
                current.getFullName(),
                buildChanges(beforeState, afterState)
        );
    }

    private List<UserAuditDiffResponse.FieldChange> buildChanges(User before, User after) {
        List<UserAuditDiffResponse.FieldChange> changes = new ArrayList<>();
        diff(changes, "fullName",        before, after, User::getFullName);
        diff(changes, "email",           before, after, User::getEmail);
        diff(changes, "cin",             before, after, User::getCin);
        diff(changes, "phoneNumber",     before, after, User::getPhoneNumber);
        diff(changes, "address",         before, after, User::getAddress);
        diff(changes, "roles",           before, after, User::getRoles);
        // enabled is a primitive — handle null (deleted/created) explicitly
        Object beforeEnabled = before != null ? before.isEnabled() : null;
        Object afterEnabled  = after  != null ? after.isEnabled()  : null;
        if (!Objects.equals(beforeEnabled, afterEnabled)) {
            changes.add(new UserAuditDiffResponse.FieldChange("enabled", beforeEnabled, afterEnabled));
        }
        return changes;
    }

    private <T> void diff(List<UserAuditDiffResponse.FieldChange> changes, String field,
                           User before, User after, Function<User, T> getter) {
        T b = before != null ? getter.apply(before) : null;
        T a = after  != null ? getter.apply(after)  : null;
        if (!Objects.equals(b, a)) {
            changes.add(new UserAuditDiffResponse.FieldChange(field, b, a));
        }
    }

    private AuditQuery buildQuery(AuditReader reader, String performedBy, String ip,
                                   AuditAction action, UUID userId, String date) {
        AuditQuery q = reader.createQuery()
                .forRevisionsOfEntity(User.class, false, true);

        if (performedBy != null) {
            q.add(AuditEntity.revisionProperty("email")
                    .like("%" + performedBy + "%"));
        }
        if (ip != null) {
            q.add(AuditEntity.revisionProperty("ip")
                    .like("%" + ip + "%"));
        }
        if (action != null) {
            RevisionType revType = switch (action) {
                case CREATE -> RevisionType.ADD;
                case UPDATE -> RevisionType.MOD;
                case DELETE -> RevisionType.DEL;
            };
            q.add(AuditEntity.revisionType().eq(revType));
        }
        if (userId != null) {
            q.add(AuditEntity.id().eq(userId));
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