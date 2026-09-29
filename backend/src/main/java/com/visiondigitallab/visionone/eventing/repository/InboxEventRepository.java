package com.visiondigitallab.visionone.eventing.repository;

import com.visiondigitallab.visionone.eventing.domain.InboxEvent;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface InboxEventRepository extends JpaRepository<InboxEvent, UUID> {

    /** Same SKIP LOCKED claim as the outbox, so more than one instance can drain it. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@jakarta.persistence.QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
    @Query("select e from InboxEvent e where e.processedAt is null and e.attempts < :maxAttempts "
            + "order by e.receivedAt asc")
    List<InboxEvent> claimUnprocessed(@Param("maxAttempts") int maxAttempts, Pageable pageable);

    long countByProcessedAtIsNull();
}
