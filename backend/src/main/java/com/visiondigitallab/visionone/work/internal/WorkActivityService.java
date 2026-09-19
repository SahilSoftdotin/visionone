package com.visiondigitallab.visionone.work.internal;

import com.visiondigitallab.visionone.work.api.WorkActivity;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WorkActivityService implements WorkActivity {

    private final JdbcClient jdbc;

    public WorkActivityService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public ActivityCounts countsFor(UUID organizationId, Instant from, Instant to) {
        // Completed is windowed to the period; in-progress and waiting are current state,
        // because "what needs my attention" is a question about now, not about last month.
        return jdbc.sql("""
                select
                  count(*) filter (where status = 'COMPLETED'
                                   and completed_at >= :from and completed_at < :to) as completed,
                  count(*) filter (where status = 'IN_PROGRESS')        as in_progress,
                  count(*) filter (where status = 'WAITING_FOR_CLIENT') as waiting_for_client
                from work_item
                where organization_id = :orgId
                """)
                .param("orgId", organizationId)
                .param("from", java.sql.Timestamp.from(from))
                .param("to", java.sql.Timestamp.from(to))
                .query((rs, rowNum) -> new ActivityCounts(
                        rs.getLong("completed"), rs.getLong("in_progress"), rs.getLong("waiting_for_client")))
                .single();
    }
}
