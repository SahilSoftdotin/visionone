package com.visiondigitallab.visionone.reporting.domain;

/** Where a monthly report is in its short life. */
public enum ReportStatus {

    /** Narrative being written; figures still move as the month's data lands. */
    DRAFT,

    /** Figures frozen into the payload. Vision can still edit the narrative. */
    GENERATED,

    /** Visible to the client. Nothing about it changes after this. */
    SHARED;

    public boolean canMoveTo(ReportStatus target) {
        return switch (this) {
            case DRAFT -> target == GENERATED;
            case GENERATED -> target == GENERATED || target == SHARED;
            case SHARED -> false;
        };
    }
}
