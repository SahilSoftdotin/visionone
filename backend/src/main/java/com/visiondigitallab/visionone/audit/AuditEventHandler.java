package com.visiondigitallab.visionone.audit;

import com.visiondigitallab.visionone.eventing.api.DomainEventHandler;
import com.visiondigitallab.visionone.eventing.api.EventEnvelope;
import com.visiondigitallab.visionone.eventing.api.EventType;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Writes an audit row for every domain event.
 *
 * <p>Read-only with respect to other modules: it derives its own table and never writes theirs.
 */
@Component
public class AuditEventHandler implements DomainEventHandler {

    private static final String GROUP = "visionone-audit";

    private final AuditEventRepository auditEvents;

    public AuditEventHandler(AuditEventRepository auditEvents) {
        this.auditEvents = auditEvents;
    }

    @Override
    public String consumerGroup() {
        return GROUP;
    }

    @Override
    public Set<EventType> handles() {
        return EnumSet.allOf(EventType.class);
    }

    @Override
    public void handle(EventEnvelope envelope) {
        auditEvents.save(new AuditEvent(
                UUID.randomUUID(),
                envelope.organizationId(),
                envelope.source(),
                envelope.eventType(),
                envelope.eventType(),
                null,
                envelope.payload() == null ? "{}" : envelope.payload().toString()));
    }
}
