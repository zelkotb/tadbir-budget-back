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

import ma.zakaria.tadbirbudget.entity.OrgUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrgUnitRepository extends JpaRepository<OrgUnit, UUID> {

    boolean existsByParentId(UUID parentId);

    List<OrgUnit> findAllByOrderByPathAsc();

    /** The whole subtree (the node itself included) — path prefix match, tree order. */
    List<OrgUnit> findByPathStartingWithOrderByPathAsc(String pathPrefix);

    /**
     * The given unit and all its ancestors — every unit whose path is a prefix of {@code targetPath}
     * (i.e. {@code targetPath LIKE path || '%'}). Used to resolve "which assignments reach this unit"
     * (an assignment to any ancestor is usable here).
     */
    @Query(value = "SELECT * FROM org_unit WHERE :targetPath LIKE path || '%'", nativeQuery = true)
    List<OrgUnit> findSelfAndAncestorsByPath(@Param("targetPath") String targetPath);

    /**
     * Bulk-rewrites the materialized path (and depth) of a moved node's whole subtree in one
     * statement. Runs outside the persistence context ({@code clearAutomatically}) and bypasses
     * Envers — fine, because path/depth are derived bookkeeping ({@code @NotAudited}); the
     * audited business fact is the node's {@code parent_id} change.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE org_unit SET "
            + "path = CONCAT(:newPrefix, SUBSTRING(path, :oldPrefixLength + 1)), "
            + "depth = depth + :depthDelta "
            + "WHERE path LIKE CONCAT(:oldPrefix, '%')", nativeQuery = true)
    int rebasePaths(@Param("oldPrefix") String oldPrefix,
                    @Param("oldPrefixLength") int oldPrefixLength,
                    @Param("newPrefix") String newPrefix,
                    @Param("depthDelta") int depthDelta);
}
