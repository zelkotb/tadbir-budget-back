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
package ma.zakaria.tadbirbudget.entity.enums;

/**
 * Lifecycle of a {@link ma.zakaria.tadbirbudget.entity.Nomenclature}.
 *
 * <ul>
 *   <li>{@code DRAFT}    — being built; its rubriques (tree) can be added / edited / deleted.</li>
 *   <li>{@code FIXED}    — locked; the tree is immutable (real budgets can be built on it).</li>
 *   <li>{@code ARCHIVED} — retired; kept for history, not used for new work.</li>
 * </ul>
 */
public enum NomenclatureStatus {
    DRAFT,
    FIXED,
    ARCHIVED
}
