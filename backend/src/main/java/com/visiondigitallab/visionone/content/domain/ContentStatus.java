package com.visiondigitallab.visionone.content.domain;

import java.util.Set;

/**
 * The content pipeline, with the transitions Vision Digital Lab is allowed to make.
 *
 * <p>Two gates, held by different people. Vision holds the editorial one - is this good enough to
 * show the client - by moving INTERNAL_REVIEW to CLIENT_REVIEW. The client holds the publication
 * one, and APPROVED and CHANGES_REQUESTED are reachable only through their own decision.
 *
 * <p>SCHEDULED and PUBLISHED are reachable only from APPROVED. That is the point of the gate:
 * refusing to let Vision set APPROVED means nothing if Vision can publish without it.
 */
public enum ContentStatus {
    IDEA,
    DRAFTING,
    INTERNAL_REVIEW,
    /** Waiting on the practice. The only state where the client has something to do. */
    CLIENT_REVIEW,
    CHANGES_REQUESTED,
    APPROVED,
    SCHEDULED,
    PUBLISHED;

    /**
     * Where Vision may move an item from here.
     *
     * <p>Moving backwards is always allowed: withdrawing something from the client's queue, or
     * sending an approved piece back for another pass, is retracting rather than approving.
     */
    public Set<ContentStatus> visionCanMoveTo() {
        return switch (this) {
            case IDEA -> Set.of(DRAFTING);
            // Internal review is available, not compulsory. It is Vision's own quality check and it
            // protects nobody but Vision, so forcing a draft through it would be process for its
            // own sake. The gate that matters is the client's, below.
            case DRAFTING -> Set.of(IDEA, INTERNAL_REVIEW, CLIENT_REVIEW);
            case INTERNAL_REVIEW -> Set.of(DRAFTING, CLIENT_REVIEW);
            // Withdrawing from the client's queue, not deciding for them.
            case CLIENT_REVIEW -> Set.of(DRAFTING);
            // Resubmitting after acting on the feedback, without a round trip through DRAFTING.
            case CHANGES_REQUESTED -> Set.of(DRAFTING, CLIENT_REVIEW);
            // Only from here does publishing become available, and only the client gets here.
            case APPROVED -> Set.of(DRAFTING, SCHEDULED, PUBLISHED);
            case SCHEDULED -> Set.of(APPROVED, PUBLISHED);
            // A published piece is corrected by editing it, not by moving it.
            case PUBLISHED -> Set.of();
        };
    }
}
