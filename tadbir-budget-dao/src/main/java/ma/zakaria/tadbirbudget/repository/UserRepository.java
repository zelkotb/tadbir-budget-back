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
package ma.zakaria.tadbirbudget.repository;

import ma.zakaria.tadbirbudget.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    /** Lookup by login identifier ({@code uid}) — used by authentication. */
    Optional<User> findByUid(String uid);
    boolean existsByUid(String uid);
    boolean existsByEmail(String email);

    boolean existsByOrgUnitId(UUID orgUnitId);
    List<User> findByOrgUnitIdOrderByFullNameAsc(UUID orgUnitId);
    List<User> findByOrgUnitIdInOrderByFullNameAsc(List<UUID> orgUnitIds);

    /** Direct reports — users whose N+1 (manager) is the given user. */
    List<User> findByManagerId(UUID managerId);

    /**
     * Enabled users holding the given role. {@code roles} is a comma-joined string
     * (see {@link ma.zakaria.tadbirbudget.converter.StringListConverter}); a plain
     * substring match is safe because, by convention, no role name is a substring
     * of another (see {@link ma.zakaria.tadbirbudget.constant.Roles}).
     * Used by the workflow ROLE approver resolver.
     */
    @Query(value = "SELECT * FROM users WHERE enabled = true "
            + "AND roles LIKE CONCAT('%', :role, '%')", nativeQuery = true)
    List<User> findByRoleContaining(@Param("role") String role);

    /**
     * All users, ordered by full name. Used to populate the "manager (N+1)" picker —
     * any user may be another user's hierarchical superior.
     */
    @Query(value = "SELECT * FROM users ORDER BY full_name", nativeQuery = true)
    List<User> findStaff();
}