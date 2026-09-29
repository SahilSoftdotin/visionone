package com.visiondigitallab.visionone.work.domain;

public enum WorkStatus {
    PLANNED,
    IN_PROGRESS,
    BLOCKED,
    /** Vision cannot proceed until the practice does something. Drives the Overview attention list. */
    WAITING_FOR_CLIENT,
    COMPLETED,
    CANCELLED
}
