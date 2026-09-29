package com.visiondigitallab.visionone.eventing;

import static org.assertj.core.api.Assertions.assertThat;

import com.visiondigitallab.visionone.eventing.api.DomainEventHandler;
import com.visiondigitallab.visionone.eventing.api.DomainEventPublisher;
import com.visiondigitallab.visionone.eventing.api.EventEnvelope;
import com.visiondigitallab.visionone.eventing.api.EventType;
import com.visiondigitallab.visionone.eventing.internal.OutboxDispatcher;
import com.visiondigitallab.visionone.support.PostgresIntegrationTest;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Proves the three properties that used to be Kafka's job, now that Postgres is the queue.
 *
 * <p>Atomicity, at-least-once delivery with exactly-once effect, and a failing handler that does
 * not lose the event or block the others.
 */
@Import(OutboxDispatcherTest.Handlers.class)
class OutboxDispatcherTest extends PostgresIntegrationTest {

    private static final UUID THRIVE = UUID.fromString("0199a1d0-0000-7000-8000-000000000001");

    @Autowired
    private DomainEventPublisher publisher;

    @Autowired
    private OutboxDispatcher dispatcher;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactions;

    @Autowired
    private CountingHandler counting;

    @Autowired
    private FailingHandler failing;

    @BeforeEach
    void reset() {
        transactions = new TransactionTemplate(transactionManager);
        jdbc.sql("delete from audit_event").update();
        jdbc.sql("delete from processed_event").update();
        jdbc.sql("delete from outbox_event").update();
        counting.seen.set(0);
        failing.shouldFail.set(false);
        failing.seen.set(0);
    }

    @Test
    @DisplayName("a rolled-back transaction leaves no event behind")
    void rollbackTakesTheEventWithIt() {
        try {
            transactions.execute(status -> {
                publisher.publish(EventType.BUDGET_UPDATED, THRIVE, "GrowthPlan", UUID.randomUUID(),
                        java.util.Map.of("plannedTotalMinor", 500000));
                throw new IllegalStateException("something went wrong after publishing");
            });
        } catch (IllegalStateException expected) {
            // The point of the test.
        }

        assertThat(outboxCount()).isZero();
    }

    @Test
    @DisplayName("a committed event reaches every interested handler and is marked published")
    void committedEventIsDelivered() {
        publish();

        dispatcher.drain();

        assertThat(counting.seen.get()).isEqualTo(1);
        assertThat(unpublishedCount()).isZero();
    }

    @Test
    @DisplayName("a redelivered event is claimed once and applied once")
    void redeliveryHasNoSecondEffect() {
        publish();

        dispatcher.drain();
        assertThat(counting.seen.get()).isEqualTo(1);

        // Draining again is not enough on its own: the row is published, so it is never re-claimed
        // and the guard is never reached. Put it back on the queue to force an actual redelivery -
        // which is what a crash between applying and marking published looks like.
        jdbc.sql("update outbox_event set published_at = null").update();

        dispatcher.drain();

        assertThat(counting.seen.get())
                .as("the handler must not run a second time for the same event")
                .isEqualTo(1);
        // Claimed once, and the claim survives.
        assertThat(claimCount("test-counting")).isEqualTo(1);
        // Still marked published: a handler that was skipped counts as succeeded.
        assertThat(unpublishedCount()).isZero();
    }

    @Test
    @DisplayName("a failing handler leaves the event unpublished for retry, and recovers")
    void failingHandlerIsRetriedWithoutLosingTheEvent() {
        failing.shouldFail.set(true);
        publish();

        dispatcher.drain();

        // Not published, because one handler failed. The other handler already did its work.
        assertThat(unpublishedCount()).isEqualTo(1);
        assertThat(counting.seen.get()).isEqualTo(1);
        assertThat(attempts()).isGreaterThanOrEqualTo(1);

        failing.shouldFail.set(false);
        dispatcher.drain();

        assertThat(unpublishedCount()).isZero();
        // The handler that already succeeded is skipped by its own processed_event row.
        assertThat(counting.seen.get()).isEqualTo(1);
        assertThat(failing.seen.get()).isEqualTo(1);
    }

    private void publish() {
        transactions.execute(status -> {
            publisher.publish(EventType.BUDGET_UPDATED, THRIVE, "GrowthPlan", UUID.randomUUID(),
                    java.util.Map.of("plannedTotalMinor", 500000));
            return null;
        });
    }

    private long outboxCount() {
        return jdbc.sql("select count(*) from outbox_event").query(Long.class).single();
    }

    private long unpublishedCount() {
        return jdbc.sql("select count(*) from outbox_event where published_at is null")
                .query(Long.class).single();
    }

    private long claimCount(String consumerGroup) {
        return jdbc.sql("select count(*) from processed_event where consumer_group = :g")
                .param("g", consumerGroup)
                .query(Long.class)
                .single();
    }

    private int attempts() {
        return jdbc.sql("select coalesce(max(attempts), 0) from outbox_event")
                .query(Integer.class).single();
    }

    /** Test doubles registered as real handlers, so the dispatcher finds them the normal way. */
    @TestConfiguration
    static class Handlers {

        @Bean
        CountingHandler countingHandler() {
            return new CountingHandler();
        }

        @Bean
        FailingHandler failingHandler() {
            return new FailingHandler();
        }
    }

    static class CountingHandler implements DomainEventHandler {
        final AtomicInteger seen = new AtomicInteger();

        @Override
        public String consumerGroup() {
            return "test-counting";
        }

        @Override
        public Set<EventType> handles() {
            return EnumSet.of(EventType.BUDGET_UPDATED);
        }

        @Override
        public void handle(EventEnvelope envelope) {
            seen.incrementAndGet();
        }
    }

    static class FailingHandler implements DomainEventHandler {
        final AtomicBoolean shouldFail = new AtomicBoolean();
        final AtomicInteger seen = new AtomicInteger();

        @Override
        public String consumerGroup() {
            return "test-failing";
        }

        @Override
        public Set<EventType> handles() {
            return EnumSet.of(EventType.BUDGET_UPDATED);
        }

        @Override
        public void handle(EventEnvelope envelope) {
            if (shouldFail.get()) {
                throw new IllegalStateException("handler refused");
            }
            seen.incrementAndGet();
        }
    }
}
