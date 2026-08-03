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

import ma.zakaria.tadbirbudget.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID>, JpaSpecificationExecutor<Project> {

    List<Project> findByOrgUnitIdInOrderByNameAsc(Collection<UUID> orgUnitIds);

    /**
     * Projects visible to a manager: those whose <b>chef</b> is one of {@code chefIds} (the caller and
     * their direct reports) <b>or</b> whose org unit is in {@code orgUnitIds} (the caller's subtree).
     * Either collection may be empty (Hibernate treats an empty {@code IN} as no-match).
     */
    @Query("select p from Project p where p.chefProjetId in :chefIds or p.orgUnitId in :orgUnitIds "
            + "order by p.name asc")
    List<Project> findByChefOrOrgUnit(@Param("chefIds") Collection<UUID> chefIds,
                                      @Param("orgUnitIds") Collection<UUID> orgUnitIds);

    List<Project> findAllByOrderByNameAsc();

    boolean existsByOrgUnitId(UUID orgUnitId);
}
