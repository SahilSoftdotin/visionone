package com.visiondigitallab.visionone.growth.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** What Vision Admin sends to set a month's budget. Amounts are minor units. */
public record GrowthPlanUpdateRequest(
        @NotNull @Min(0) Long plannedTotalMinor,
        @Size(max = 4000) String notes) {}
