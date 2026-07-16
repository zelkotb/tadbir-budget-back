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
package ma.zakaria.tadbirbudget.workflow.dto;

import java.time.Instant;

/**
 * One node of the request's computed stepper: a distinct phase of the process, in order.
 * Repeats caused by a RETURN collapse into a single node (the per-round detail lives in the
 * step-history "Parcours"). The {@code key} is the task definition key — the frontend maps it
 * to a localized label.
 */
public record StepperNode(String key, State state, Instant at) {

    public enum State {
        /** Already passed (the process has moved beyond this phase). */
        DONE,
        /** The phase the request is currently at. */
        CURRENT,
        /** Not reached yet (or to be re-done after a RETURN). */
        UPCOMING
    }

    public static StepperNode done(String key, Instant at) {
        return new StepperNode(key, State.DONE, at);
    }

    public static StepperNode current(String key) {
        return new StepperNode(key, State.CURRENT, null);
    }

    public static StepperNode upcoming(String key) {
        return new StepperNode(key, State.UPCOMING, null);
    }
}
