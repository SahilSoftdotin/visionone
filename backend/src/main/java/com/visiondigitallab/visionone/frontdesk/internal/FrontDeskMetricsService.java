package com.visiondigitallab.visionone.frontdesk.internal;

import com.visiondigitallab.visionone.frontdesk.api.FrontDeskMetrics;
import com.visiondigitallab.visionone.frontdesk.domain.Call;
import com.visiondigitallab.visionone.frontdesk.repository.CallRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FrontDeskMetricsService implements FrontDeskMetrics {

    private final CallRepository calls;

    public FrontDeskMetricsService(CallRepository calls) {
        this.calls = calls;
    }

    @Override
    public CallActivity activityFor(UUID organizationId, Instant from, Instant to, ZoneId zone) {
        List<Call> period = calls
                .findByOrganizationIdAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtDesc(
                        organizationId, from, to);
        return new CallActivity(CallSummaries.summarise(period), CallSummaries.hourly(period, zone));
    }
}
