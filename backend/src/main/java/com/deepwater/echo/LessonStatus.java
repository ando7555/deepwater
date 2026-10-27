package com.deepwater.echo;

/**
 * Lesson lifecycle: DRAFT -> PUBLISHED -> UNPUBLISHED -> PUBLISHED, and PUBLISHED or UNPUBLISHED -> ARCHIVED
 * when a revision is published. ARCHIVED is terminal; UNPUBLISHED means withdrawn by the teacher and may return.
 * The transitions are enforced by the conditional updates in {@link EchoService}, so they stay atomic.
 */
public enum LessonStatus {
    DRAFT, PUBLISHED, UNPUBLISHED, ARCHIVED
}
