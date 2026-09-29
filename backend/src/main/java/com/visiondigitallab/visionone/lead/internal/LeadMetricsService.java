package com.visiondigitallab.visionone.lead.internal;

import com.visiondigitallab.visionone.lead.api.LeadListResponse;
import com.visiondigitallab.visionone.lead.api.LeadMetrics;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LeadMetricsService implements LeadMetrics {

    /**
     * A lead counts at a funnel stage if its history ever reached that stage, so a lead that
     * jumped straight to BOOKED still appears at Qualified and Appointment Requested.
     */
    private static final String EVER_REACHED =
            """
            exists (select 1 from lead_status_history h
                    where h.lead_id = l.id and h.to_status in (:statuses))
            """;

    private final JdbcClient jdbc;

    public LeadMetricsService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long newLeadCount(UUID organizationId, Instant from, Instant to) {
        return jdbc.sql("""
                select count(*) from lead l
                where l.organization_id = :orgId
                  and l.created_at >= :from and l.created_at < :to
                  and l.status <> 'DUPLICATE'
                """)
                .param("orgId", organizationId)
                .param("from", java.sql.Timestamp.from(from))
                .param("to", java.sql.Timestamp.from(to))
                .query(Long.class)
                .single();
    }

    @Override
    public List<LeadListResponse.LeadRow> recentIn(
            UUID organizationId, Instant from, Instant to, int limit) {
        return jdbc.sql(LeadQueryService.ROW_SELECT + """
                 where l.organization_id = :orgId
                   and l.created_at >= :from and l.created_at < :to
                   and l.status <> 'DUPLICATE'
                 order by l.created_at desc
                 limit :limit
                """)
                .param("orgId", organizationId)
                .param("from", java.sql.Timestamp.from(from))
                .param("to", java.sql.Timestamp.from(to))
                .param("limit", limit)
                .query(LeadQueryService::toRow)
                .list();
    }

    @Override
    public Funnel funnel(UUID organizationId, Instant from, Instant to) {
        return jdbc.sql("""
                select
                  count(*) filter (where true) as leads,
                  count(*) filter (where %s) as qualified,
                  count(*) filter (where %s) as appointment_requested,
                  count(*) filter (where %s) as booked
                from lead l
                where l.organization_id = :orgId
                  and l.created_at >= :from and l.created_at < :to
                  and l.status <> 'DUPLICATE'
                """.formatted(
                        everReached("qualifiedStatuses"),
                        everReached("requestedStatuses"),
                        everReached("bookedStatuses")))
                .param("orgId", organizationId)
                .param("from", java.sql.Timestamp.from(from))
                .param("to", java.sql.Timestamp.from(to))
                .param("qualifiedStatuses", qualifiedOnwards())
                .param("requestedStatuses", requestedOnwards())
                .param("bookedStatuses", bookedOnwards())
                .query((rs, rowNum) -> new Funnel(
                        rs.getLong("leads"),
                        rs.getLong("qualified"),
                        rs.getLong("appointment_requested"),
                        rs.getLong("booked")))
                .single();
    }

    @Override
    public List<SourceCounts> countsBySource(UUID organizationId, Instant from, Instant to) {
        return jdbc.sql("""
                select cs.code as channel_code,
                       count(l.id) as leads,
                       count(l.id) filter (where %s) as qualified,
                       count(l.id) filter (where %s) as booked
                from channel_source cs
                left join lead l
                       on l.channel_source_id = cs.id
                      and l.organization_id = cs.organization_id
                      and l.created_at >= :from and l.created_at < :to
                      and l.status <> 'DUPLICATE'
                where cs.organization_id = :orgId and cs.active
                group by cs.code, cs.sort_order
                order by cs.sort_order
                """.formatted(everReached("qualifiedStatuses"), everReached("bookedStatuses")))
                .param("orgId", organizationId)
                .param("from", java.sql.Timestamp.from(from))
                .param("to", java.sql.Timestamp.from(to))
                .param("qualifiedStatuses", qualifiedOnwards())
                .param("bookedStatuses", bookedOnwards())
                .query((rs, rowNum) -> new SourceCounts(
                        rs.getString("channel_code"), rs.getLong("leads"),
                        rs.getLong("qualified"), rs.getLong("booked")))
                .list();
    }

    private static String everReached(String parameterName) {
        return EVER_REACHED.replace(":statuses", ":" + parameterName);
    }

    private static List<String> qualifiedOnwards() {
        return List.of("QUALIFIED", "APPOINTMENT_REQUESTED", "BOOKED", "ATTENDED");
    }

    private static List<String> requestedOnwards() {
        return List.of("APPOINTMENT_REQUESTED", "BOOKED", "ATTENDED");
    }

    private static List<String> bookedOnwards() {
        return List.of("BOOKED", "ATTENDED");
    }
}
