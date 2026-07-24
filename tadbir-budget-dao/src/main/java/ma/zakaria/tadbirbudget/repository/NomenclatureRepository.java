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

import ma.zakaria.tadbirbudget.entity.Nomenclature;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NomenclatureRepository extends JpaRepository<Nomenclature, UUID> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNomenclatureDefinitionId(UUID nomenclatureDefinitionId);

    List<Nomenclature> findAllByOrderByNameAscVersionAsc();

    List<Nomenclature> findByLineageIdOrderByVersionAsc(UUID lineageId);

    @Query("select coalesce(max(n.version), 0) from Nomenclature n where n.lineageId = :lineageId")
    int maxVersionByLineageId(@Param("lineageId") UUID lineageId);
}
