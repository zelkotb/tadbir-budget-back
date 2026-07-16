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
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

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
     * Staff users — everyone holding a role other than the citizen role (ROLE_USER). Used to
     * populate the "manager (N+1)" picker. Substring match is safe (no role name is a substring
     * of another, by convention).
     */
    @Query(value = "SELECT * FROM users WHERE "
            + "roles LIKE '%ROLE_ADMIN%' OR roles LIKE '%ROLE_INSTRUCTOR%' "
            + "OR roles LIKE '%ROLE_VALIDATOR%' OR roles LIKE '%ROLE_COMMISSION%' "
            + "OR roles LIKE '%ROLE_MEMBRE_COMMISSION%' OR roles LIKE '%ROLE_MANAGEMENT%' "
            + "ORDER BY full_name", nativeQuery = true)
    List<User> findStaff();
}