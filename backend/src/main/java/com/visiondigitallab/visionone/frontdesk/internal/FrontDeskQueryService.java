package com.visiondigitallab.visionone.frontdesk.internal;

import com.visiondigitallab.visionone.frontdesk.api.FrontDeskResponse;
import com.visiondigitallab.visionone.frontdesk.domain.Call;
import com.visiondigitallab.visionone.frontdesk.domain.CallOutcome;
import com.visiondigitallab.visionone.frontdesk.repository.CallRepository;
import com.visiondigitallab.visionone.integration.api.ProviderStatus;
import com.visiondigitallab.visionone.integration.api.VoiceProvider;
import com.visiondigitallab.visionone.tenant.api.OrganizationContext;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the Front Desk screen from stored calls.
 *
 * <p>Deliberately not from the provider. The port's own contract says implementations run from a
 * scheduled sync: a telephony vendor being slow must not make VisionOne slow, and a dashboard that
 * blanks when a vendor rate-limits is worse than one showing figures with an as-of time. The
 * provider is consulted for one thing only - what to call the data on screen.
 */
@Service
@Transactional(readOnly = true)
public class FrontDeskQueryService {

    private static final int RECENT_LIMIT = 12;

    private final CallRepository calls;
    private final VoiceProvider voiceProvider;

    public FrontDeskQueryService(CallRepository calls, VoiceProvider voiceProvider) {
        this.calls = calls;
        this.voiceProvider = voiceProvider;
    }

    public FrontDeskResponse read(OrganizationContext context, YearMonth month) {
        return read(context, month, null);
    }

    /**
     * The Front Desk screen, optionally with the call list narrowed to one outcome.
     *
     * <p>The filter reaches the table and nothing else. Summary, hourly volume and the outcome
     * counts are always computed over the whole month, so choosing "Missed" does not renumber the
     * tiles above the table or the pills beside it. A filter that rewrites the figures it sits
     * under is a filter people stop trusting.
     */
    public FrontDeskResponse read(OrganizationContext context, YearMonth month, String outcome) {
        ZoneId zone = context.zoneId();
        Instant from = month.atDay(1).atStartOfDay(zone).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant();

        List<Call> period = calls
                .findByOrganizationIdAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtDesc(
                        context.organizationId(), from, to);

        CallOutcome selected = parseOutcome(outcome);
        List<FrontDeskResponse.CallRow> recent = period.stream()
                .filter(call -> selected == null || call.getOutcome() == selected)
                .limit(RECENT_LIMIT)
                .map(FrontDeskQueryService::toRow)
                .toList();

        return new FrontDeskResponse(
                month.atDay(1),
                describeSource(context),
                CallSummaries.summarise(period),
                CallSummaries.hourly(period, zone),
                CallSummaries.outcomeCounts(period),
                recent);
    }

    /**
     * An outcome the caller asked for, or null for all of them.
     *
     * <p>An unrecognised value reads as no filter rather than as an error. The parameter comes from
     * a URL, where a stale link or a typo is ordinary; answering a hand-edited address bar with a
     * 400 helps nobody, and the reader still gets the screen they asked for.
     */
    private static CallOutcome parseOutcome(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return CallOutcome.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    /**
     * What the badge on the screen says.
     *
     * <p>An adapter reporting ERROR is named as such. "No calls this week" and "we could not ask"
     * are different sentences, and a dashboard that blurs them is lying quietly.
     */
    private String describeSource(OrganizationContext context) {
        ProviderStatus status = voiceProvider.status(context.organizationId());
        return switch (status) {
            case CONNECTED -> "LIVE";
            case ERROR -> "ERROR";
            case NEEDS_AUTHORIZATION -> "NEEDS_AUTHORIZATION";
            case NOT_CONNECTED -> "NOT_CONNECTED";
            case DEMO -> "DEMO";
        };
    }

    private static FrontDeskResponse.CallRow toRow(Call call) {
        return new FrontDeskResponse.CallRow(
                call.getId(),
                call.getCallerLabel(),
                call.getHandledBy() == null ? "UNKNOWN" : call.getHandledBy().name(),
                call.getOutcome().name(),
                call.isAfterHours(),
                call.getDurationSeconds(),
                call.getStartedAt().toString());
    }
}
