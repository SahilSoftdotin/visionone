package com.visiondigitallab.visionone.eventing.repository;

import com.visiondigitallab.visionone.eventing.domain.OutboxEvent;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Claims a batch of unpublished events.
     *
     * <p>SKIP LOCKED makes a second application instance safe without any further coordination,
     * which is the reason Phase 1 needs no distributed lock and no Valkey.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select e from OutboxEvent e where e.publishedAt is null and e.attempts < :maxAttempts "
            + "order by e.occurredAt asc")
    List<OutboxEvent> claimUnpublished(@Param("maxAttempts") int maxAttempts,
            org.springframework.data.domain.Pageable pageable);

    long countByPublishedAtIsNull();
}
