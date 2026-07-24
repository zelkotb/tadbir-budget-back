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

import ma.zakaria.tadbirbudget.entity.NomenclatureRubrique;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NomenclatureRubriqueRepository extends JpaRepository<NomenclatureRubrique, UUID> {

    List<NomenclatureRubrique> findByNomenclatureIdOrderByLevelPositionAscCodeAsc(UUID nomenclatureId);

    // Code is unique among SIBLINGS (same parent), case-insensitive — roots (parent null) handled
    // by the *IsNull* variants because a null parameter would not match a NULL column.
    boolean existsByNomenclatureIdAndParentIdIsNullAndCodeIgnoreCase(UUID nomenclatureId, String code);
    boolean existsByNomenclatureIdAndParentIdIsNullAndCodeIgnoreCaseAndIdNot(
            UUID nomenclatureId, String code, UUID id);
    boolean existsByNomenclatureIdAndParentIdAndCodeIgnoreCase(UUID nomenclatureId, UUID parentId, String code);
    boolean existsByNomenclatureIdAndParentIdAndCodeIgnoreCaseAndIdNot(
            UUID nomenclatureId, UUID parentId, String code, UUID id);

    boolean existsByParentId(UUID parentId);

    long countByNomenclatureId(UUID nomenclatureId);

    void deleteByNomenclatureId(UUID nomenclatureId);
}
