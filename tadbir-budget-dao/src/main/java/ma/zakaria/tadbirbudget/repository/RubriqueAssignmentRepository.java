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

import ma.zakaria.tadbirbudget.entity.RubriqueAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface RubriqueAssignmentRepository extends JpaRepository<RubriqueAssignment, UUID> {

    boolean existsByRubriqueIdAndOrgUnitId(UUID rubriqueId, UUID orgUnitId);

    boolean existsByRubriqueId(UUID rubriqueId);

    List<RubriqueAssignment> findByNomenclatureId(UUID nomenclatureId);

    List<RubriqueAssignment> findByNomenclatureIdAndOrgUnitId(UUID nomenclatureId, UUID orgUnitId);

    /** Assignments in a nomenclature granted to any of the given org units (the caller + ancestors). */
    List<RubriqueAssignment> findByNomenclatureIdAndOrgUnitIdIn(UUID nomenclatureId, Collection<UUID> orgUnitIds);
}
