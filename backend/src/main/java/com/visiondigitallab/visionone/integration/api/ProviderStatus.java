package com.visiondigitallab.visionone.integration.api;

/**
 * How a connection is reported to the client, verbatim.
 *
 * <p>These are the same values the {@code integration_connection} check constraint allows, so the
 * screen, the API and the database cannot drift into disagreeing about whether something is
 * connected. VisionOne never shows a connection as working when it is not.
 */
public enum ProviderStatus {
    /** Synthetic data. Phase 1 runs here. */
    DEMO,

    /** No credentials configured. */
    NOT_CONNECTED,

    /** Credentials exist but the practice has not authorised access. */
    NEEDS_AUTHORIZATION,

    /** Live and answering. */
    CONNECTED,

    /** Configured, but the last exchange failed. */
    ERROR
}
