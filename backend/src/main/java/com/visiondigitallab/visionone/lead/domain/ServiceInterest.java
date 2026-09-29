package com.visiondigitallab.visionone.lead.domain;

/**
 * Broad commercial categories, never a condition or a diagnosis.
 *
 * <p>The same list is enforced by a check constraint in the database. VisionOne is a growth
 * platform: if a value here ever starts describing someone's health, the boundary has been
 * crossed.
 */
public enum ServiceInterest {
    LONGEVITY_PROGRAM("Longevity Program"),
    HORMONE_OPTIMIZATION("Hormone Optimization"),
    DIAGNOSTICS("Diagnostics"),
    WEIGHT_MANAGEMENT("Weight Management"),
    IV_THERAPY("IV Therapy"),
    GENERAL_ENQUIRY("General Enquiry");

    private final String label;

    ServiceInterest(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
