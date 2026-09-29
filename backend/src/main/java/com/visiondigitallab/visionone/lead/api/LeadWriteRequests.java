package com.visiondigitallab.visionone.lead.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** What Vision Admin sends to create, correct or move a lead. */
public final class LeadWriteRequests {

    private LeadWriteRequests() {}

    public record CreateLead(
            @NotNull UUID channelSourceId,
            UUID campaignId,
            @NotBlank @Size(max = 120) String displayName,
            @NotBlank String serviceInterest,
            UUID ownerMembershipId) {}

    public record UpdateLead(
            @NotBlank @Size(max = 120) String displayName,
            @NotBlank String serviceInterest,
            UUID campaignId,
            UUID ownerMembershipId) {}

    public record TransitionLead(@NotBlank String toStatus, @Size(max = 400) String reason) {}
}
