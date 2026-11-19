package com.deepwater.workspace;

import java.util.Map;
import java.util.Set;

/**
 * Where a workspace task is. The allowed moves are written down once, here:
 * <pre>
 * PROPOSED ──approve(owner)──► APPROVED ──start──► IN_PROGRESS ──submit evidence──► IN_REVIEW ──accept(owner)──► DONE
 *    └──reject(owner)──► REJECTED                       ▲                                 │
 *                                                       └─────────send back(owner)────────┘
 * </pre>
 * Editing an APPROVED task's text moves it back to PROPOSED, because the approval was for the old text.
 */
public enum TaskStatus {
    PROPOSED, APPROVED, REJECTED, IN_PROGRESS, IN_REVIEW, DONE;

    private static final Map<TaskStatus, Set<TaskStatus>> NEXT = Map.of(
            PROPOSED, Set.of(APPROVED, REJECTED),
            APPROVED, Set.of(IN_PROGRESS, PROPOSED),
            REJECTED, Set.of(),
            IN_PROGRESS, Set.of(IN_REVIEW),
            IN_REVIEW, Set.of(DONE, IN_PROGRESS),
            DONE, Set.of());

    public boolean canMoveTo(TaskStatus target) {
        return NEXT.get(this).contains(target);
    }
}
