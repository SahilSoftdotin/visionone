package com.visiondigitallab.visionone.eventing.domain;

/** Where an incoming webhook came from. The same list is a check constraint on the table. */
public enum InboxSource {
    HEALTHIE,
    VOICE,
    ADVERTISING,
    ANALYTICS
}
