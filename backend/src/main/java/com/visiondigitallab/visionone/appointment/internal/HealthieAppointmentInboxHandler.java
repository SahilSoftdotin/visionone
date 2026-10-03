package com.visiondigitallab.visionone.appointment.internal;

import com.visiondigitallab.visionone.eventing.api.InboxHandler;
import com.visiondigitallab.visionone.eventing.domain.InboxEvent;
import com.visiondigitallab.visionone.eventing.domain.InboxSource;
import com.visiondigitallab.visionone.integration.api.SchedulingProvider;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Turns a Healthie appointment notification into a row on the Calendar.
 *
 * <p>Healthie's webhook says only which appointment changed, so this handler's job is to go and ask
 * what it changed to, then upsert. That is also what makes it safe to run twice: it applies current
 * state rather than a diff, so a redelivery converges on the same answer rather than compounding.
 * That property is load-bearing, because Healthie sends no delivery identifier and the endpoint
 * cannot tell a retry from a new notification.
 *
 * <p><b>Which organization?</b> A Healthie webhook carries no tenant - it is addressed to a URL, not
 * to a practice. So the organization is resolved from the {@code integration_connection} row that
 * declares a HEALTHIE connection. With one practice connected that is unambiguous. With two it is
 * not, and this refuses rather than guessing: putting one clinic's bookings into another clinic's
 * dashboard is the precise failure the tenant-isolation tests exist to prevent, and a wrong guess
 * here would walk straight past every one of them, because by then the row is legitimately that
 * tenant's. Supporting a second connected practice means a per-practice webhook URL or secret, so
 * the notification identifies the tenant before anything is written.
 */
@Component
public class HealthieAppointmentInboxHandler implements InboxHandler {

    private static final Logger log = LoggerFactory.getLogger(HealthieAppointmentInboxHandler.class);

    private final SchedulingProvider provider;
    private final AppointmentSyncService sync;
    private final JdbcClient jdbc;

    public HealthieAppointmentInboxHandler(
            SchedulingProvider provider, AppointmentSyncService sync, JdbcClient jdbc) {
        this.provider = provider;
        this.sync = sync;
        this.jdbc = jdbc;
    }

    @Override
    public InboxSource source() {
        return InboxSource.HEALTHIE;
    }

    @Override
    public boolean handles(String eventType) {
        // Healthie also emits FormAnswerGroup, Entry and Note events. Those are clinical content and
        // this product has no business reading them, so they are not handled - an unhandled event is
        // left alone rather than quietly swallowed.
        return eventType != null && eventType.startsWith("appointment.");
    }

    @Override
    public UUID process(InboxEvent event) {
        UUID organizationId = resolveOrganization();
        String externalRef = event.getResourceId();

        if (externalRef == null || externalRef.isBlank()) {
            throw new IllegalStateException("Healthie event " + event.getId() + " names no resource");
        }

        if ("appointment.deleted".equals(event.getEventType())) {
            // Nothing to fetch - it is gone. Mark it cancelled rather than deleting the row: it is
            // the only record the booking ever existed, and the month's figures are built on history.
            int updated = sync.forget(organizationId, externalRef);
            log.info("Healthie deleted appointment {} ({} row(s) marked cancelled)", externalRef, updated);
            return organizationId;
        }

        SchedulingProvider.AppointmentReference ref = provider.appointment(organizationId, externalRef);
        if (ref == null) {
            // Verified event, but Healthie no longer has the appointment. Treat it as a cancellation
            // we were told about late rather than as a failure to retry forever.
            int updated = sync.forget(organizationId, externalRef);
            log.info("Healthie no longer has appointment {} ({} row(s) cancelled)", externalRef, updated);
            return organizationId;
        }

        sync.apply(organizationId, provider.providerCode(), List.of(ref));
        return organizationId;
    }

    private UUID resolveOrganization() {
        List<UUID> organizations = jdbc.sql("""
                select organization_id from integration_connection
                 where provider_code = 'HEALTHIE'
                 order by organization_id
                """)
                .query(UUID.class)
                .list();

        if (organizations.isEmpty()) {
            throw new IllegalStateException(
                    "A Healthie webhook arrived but no organization declares a HEALTHIE "
                            + "integration_connection, so there is nothing to attach it to");
        }
        if (organizations.size() > 1) {
            throw new IllegalStateException(
                    "%d organizations declare a HEALTHIE connection, so this webhook is ambiguous. "
                            .formatted(organizations.size())
                            + "Refusing to guess: give each practice its own webhook URL or secret "
                            + "so the notification identifies the tenant.");
        }
        return organizations.get(0);
    }
}
