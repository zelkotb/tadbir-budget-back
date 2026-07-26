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

import ma.zakaria.tadbirbudget.entity.ProjectPhase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectPhaseRepository extends JpaRepository<ProjectPhase, UUID> {

    List<ProjectPhase> findByProjectIdOrderByStartDateAscCreatedAtAsc(UUID projectId);

    List<ProjectPhase> findByProjectId(UUID projectId);
}
