package com.visiondigitallab.visionone.lead.internal;

import com.visiondigitallab.visionone.common.NotFoundException;
import com.visiondigitallab.visionone.lead.api.LeadDetailResponse;
import com.visiondigitallab.visionone.lead.api.LeadListResponse;
import com.visiondigitallab.visionone.lead.domain.LeadStatus;
import com.visiondigitallab.visionone.lead.repository.LeadStatusHistoryRepository;
import com.visiondigitallab.visionone.tenant.api.OrganizationContext;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads for the Leads screen.
 *
 * <p>SQL rather than JPA here: the list needs the channel and campaign names and the owner's
 * display name alongside each lead, and three lazy associations per row is how a list endpoint
 * turns into sixty queries.
 */
@Service
@Transactional(readOnly = true)
public class LeadQueryService {

    /** Package-private: LeadMetricsService serves the Overview's recent-leads list from it too. */
    static final String ROW_SELECT =
            """
            select l.id, l.reference, l.display_name, l.service_interest, l.status,
                   l.created_at, l.first_response_at, l.booked_at,
                   cs.display_name as source_name,
                   c.name as campaign_name,
                   m.display_name as owner_name
            from lead l
            join channel_source cs on cs.id = l.channel_source_id
            left join campaign c on c.id = l.campaign_id
            left join membership m on m.id = l.owner_membership_id
            """;

    private final JdbcClient jdbc;
    private final LeadStatusHistoryRepository history;

    public LeadQueryService(JdbcClient jdbc, LeadStatusHistoryRepository history) {
        this.jdbc = jdbc;
        this.history = history;
    }

    public LeadListResponse list(
            OrganizationContext context, YearMonth month, String status, String source) {
        Instant from = month.atDay(1).atStartOfDay(context.zoneId()).toInstant();
        Instant to = month.plusMonths(1).atDay(1).atStartOfDay(context.zoneId()).toInstant();

        StringBuilder sql = new StringBuilder(ROW_SELECT)
                .append(" where l.organization_id = :orgId")
                .append(" and l.created_at >= :from and l.created_at < :to");
        if (status != null && !status.isBlank()) {
            sql.append(" and l.status = :status");
        }
        if (source != null && !source.isBlank()) {
            sql.append(" and cs.code = :source");
        }
        sql.append(" order by l.created_at desc");

        var spec = jdbc.sql(sql.toString())
                .param("orgId", context.organizationId())
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to));
        if (status != null && !status.isBlank()) {
            spec = spec.param("status", LeadStatus.valueOf(status).name());
        }
        if (source != null && !source.isBlank()) {
            spec = spec.param("source", source);
        }

        List<LeadListResponse.LeadRow> rows = spec.query(LeadQueryService::toRow).list();
        return new LeadListResponse(summarise(rows), rows, context.isVisionAdmin());
    }

    public LeadDetailResponse detail(OrganizationContext context, UUID leadId) {
        LeadListResponse.LeadRow lead = jdbc.sql(
                        ROW_SELECT + " where l.organization_id = :orgId and l.id = :leadId")
                .param("orgId", context.organizationId())
                .param("leadId", leadId)
                .query(LeadQueryService::toRow)
                .optional()
                .orElseThrow(() -> new NotFoundException("Lead", leadId));

        List<LeadDetailResponse.HistoryEntry> entries = history
                .findByOrganizationIdAndLeadIdOrderByChangedAtAsc(context.organizationId(), leadId)
                .stream()
                .map(entry -> new LeadDetailResponse.HistoryEntry(
                        entry.getFromStatus() == null ? null : entry.getFromStatus().name(),
                        entry.getToStatus().name(),
                        entry.getChangedAt(),
                        entry.getChangedBy(),
                        entry.getReason()))
                .toList();

        return new LeadDetailResponse(lead, entries);
    }

    /**
     * Summary above the table.
     *
     * <p>Median rather than mean response time: one lead answered three days late would drag a mean
     * far from what the front desk actually does. Duplicates are excluded from every count.
     */
    private static LeadListResponse.Summary summarise(List<LeadListResponse.LeadRow> rows) {
        List<LeadListResponse.LeadRow> counted = rows.stream()
                .filter(row -> !LeadStatus.DUPLICATE.name().equals(row.status()))
                .toList();

        List<Long> responses = new ArrayList<>(counted.stream()
                .map(LeadListResponse.LeadRow::responseMinutes)
                .filter(java.util.Objects::nonNull)
                .sorted()
                .toList());

        Long median = responses.isEmpty() ? null : responses.get(responses.size() / 2);
        Double underFifteen = responses.isEmpty()
                ? null
                : Math.round(responses.stream().filter(minutes -> minutes <= 15).count()
                                * 1000.0d / responses.size())
                        / 10.0d;

        long qualified = counted.stream()
                .filter(row -> LeadStatus.valueOf(row.status()).isQualifiedOrBeyond())
                .count();
        long booked = counted.stream().filter(LeadListResponse.LeadRow::booked).count();

        return new LeadListResponse.Summary(counted.size(), qualified, booked, median, underFifteen);
    }

    static LeadListResponse.LeadRow toRow(ResultSet rs, int rowNum) throws SQLException {
        Instant createdAt = rs.getTimestamp("created_at").toInstant();
        Timestamp firstResponse = rs.getTimestamp("first_response_at");
        LeadStatus status = LeadStatus.valueOf(rs.getString("status"));

        return new LeadListResponse.LeadRow(
                rs.getObject("id", UUID.class),
                rs.getString("reference"),
                rs.getString("display_name"),
                rs.getString("service_interest"),
                rs.getString("source_name"),
                rs.getString("campaign_name"),
                createdAt,
                status.name(),
                rs.getString("owner_name") == null ? "Unassigned" : rs.getString("owner_name"),
                firstResponse == null
                        ? null
                        : java.time.Duration.between(createdAt, firstResponse.toInstant()).toMinutes(),
                status.isBooked());
    }
}
