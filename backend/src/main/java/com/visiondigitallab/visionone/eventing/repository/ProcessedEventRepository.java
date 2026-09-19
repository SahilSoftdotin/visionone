package com.visiondigitallab.visionone.eventing.repository;

import com.visiondigitallab.visionone.eventing.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedEventRepository
        extends JpaRepository<ProcessedEvent, ProcessedEvent.Key> {}
