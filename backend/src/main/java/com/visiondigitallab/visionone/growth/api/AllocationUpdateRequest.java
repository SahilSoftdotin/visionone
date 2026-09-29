package com.visiondigitallab.visionone.growth.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * Per-channel planned and actual spend for one month.
 *
 * <p>Sent whole rather than per row, so the allocation set is always internally consistent and a
 * half-applied edit is impossible.
 */
public record AllocationUpdateRequest(@NotEmpty @Valid List<Allocation> allocations) {

    public record Allocation(
            @NotNull UUID channelSourceId,
            @NotNull @Min(0) Long plannedMinor,
            @NotNull @Min(0) Long actualMinor) {}
}
