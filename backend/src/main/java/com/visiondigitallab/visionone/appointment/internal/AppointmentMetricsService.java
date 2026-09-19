package com.visiondigitallab.visionone.appointment.internal;

import com.visiondigitallab.visionone.appointment.api.AppointmentMetrics;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AppointmentMetricsService implements AppointmentMetrics {

    private final JdbcClient jdbc;

    public AppointmentMetricsService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public long bookedCount(UUID organizationId, Instant from, Instant to) {
        // coalesce(lead_id, id): an appointment with no lead still counts once.
        return jdbc.sql("""
                select count(distinct coalesce(a.lead_id, a.id))
                from appointment_reference a
                where a.organization_id = :orgId
                  and a.status in ('BOOKED', 'ATTENDED')
                  and coalesce(a.scheduled_for, a.requested_at) >= :from
                  and coalesce(a.scheduled_for, a.requested_at) <  :to
                """)
                .param("orgId", organizationId)
                .param("from", java.sql.Timestamp.from(from))
                .param("to", java.sql.Timestamp.from(to))
                .query(Long.class)
                .single();
    }
}
