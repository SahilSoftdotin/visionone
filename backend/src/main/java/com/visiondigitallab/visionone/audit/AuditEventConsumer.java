package com.visiondigitallab.visionone.audit;

import com.visiondigitallab.visionone.eventing.api.EventEnvelope;
import com.visiondigitallab.visionone.eventing.api.EventType;
import com.visiondigitallab.visionone.eventing.api.IdempotentConsumer;
import java.util.UUID;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Writes an audit row for every domain event.
 *
 * <p>Read-only with respect to other modules: it derives its own table and never writes theirs.
 */
@Component
public class AuditEventConsumer {

    private static final String GROUP = "visionone-audit";

    private final IdempotentConsumer idempotent;
    private final AuditEventRepository auditEvents;

    public AuditEventConsumer(IdempotentConsumer idempotent, AuditEventRepository auditEvents) {
        this.idempotent = idempotent;
        this.auditEvents = auditEvents;
    }

    @KafkaListener(
            groupId = GROUP,
            topics = {
                EventType.Topics.LEAD,
                EventType.Topics.FRONTDESK,
                EventType.Topics.APPOINTMENT,
                EventType.Topics.GROWTH,
                EventType.Topics.CONTENT,
                EventType.Topics.WORK
            })
    public void onEvent(EventEnvelope envelope) {
        idempotent.once(GROUP, envelope, this::record);
    }

    private void record(EventEnvelope envelope) {
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
