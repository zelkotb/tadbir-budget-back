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
package ma.zakaria.tadbirbudget.util;

import lombok.experimental.UtilityClass;

/**
 * Shared MDC key constants used across modules.
 * Centralised here so the value never drifts between the producer (service)
 * and the consumer (CustomRevisionListener).
 */
@UtilityClass
public class MdcKeys {

    /** Per-request keys populated by MdcFilter. */
    public static final String IP       = "ip";
    public static final String USERNAME = "username";
    public static final String METHOD   = "method";
    public static final String URI      = "uri";
}