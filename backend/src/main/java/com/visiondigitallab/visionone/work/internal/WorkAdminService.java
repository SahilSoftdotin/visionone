package com.visiondigitallab.visionone.work.internal;

import com.visiondigitallab.visionone.auth.CurrentUser;
import com.visiondigitallab.visionone.common.NotFoundException;
import com.visiondigitallab.visionone.eventing.api.DomainEventPublisher;
import com.visiondigitallab.visionone.eventing.api.EventType;
import com.visiondigitallab.visionone.tenant.api.OrganizationContext;
import com.visiondigitallab.visionone.work.api.WorkWriteRequests;
import com.visiondigitallab.visionone.work.domain.WorkCategory;
import com.visiondigitallab.visionone.work.domain.WorkItem;
import com.visiondigitallab.visionone.work.domain.WorkStatus;
import com.visiondigitallab.visionone.work.repository.WorkItemRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Vision Digital Lab's write path for its own work. The client never edits this. */
@Service
public class WorkAdminService {

    private static final String AGGREGATE = "WorkItem";

    private final WorkItemRepository workItems;
    private final DomainEventPublisher events;
    private final CurrentUser currentUser;

    public WorkAdminService(
            WorkItemRepository workItems, DomainEventPublisher events, CurrentUser currentUser) {
        this.workItems = workItems;
        this.events = events;
        this.currentUser = currentUser;
    }

    @Transactional
    public UUID create(OrganizationContext context, WorkWriteRequests.UpsertWork request) {
        WorkItem item = new WorkItem(
                UUID.randomUUID(),
                context.organizationId(),
                request.title(),
                parseCategory(request.category()),
                request.businessReason(),
                request.owner());
        item.edit(request.title(), parseCategory(request.category()), request.businessReason(),
                request.owner(), request.targetDate(), request.clientUpdate());
        workItems.save(item);
        return item.getId();
    }

    @Transactional
    public void update(OrganizationContext context, UUID id, WorkWriteRequests.UpsertWork request) {
        WorkItem item = require(context, id);
        item.edit(request.title(), parseCategory(request.category()), request.businessReason(),
                request.owner(), request.targetDate(), request.clientUpdate());
        workItems.save(item);
    }

    @Transactional
    public void transition(OrganizationContext context, UUID id, WorkWriteRequests.TransitionWork request) {
        WorkItem item = require(context, id);
        boolean completed = item.transitionTo(parseStatus(request.toStatus()));
        workItems.save(item);

        // Only completion is an event. "Moved to in progress" is not something another module needs.
        if (completed) {
            events.publish(
                    EventType.WORK_COMPLETED,
                    context.organizationId(),
                    AGGREGATE,
                    item.getId(),
                    new WorkCompletedPayload(
                            item.getId(),
                            item.getTitle(),
                            item.getCategory().name(),
                            item.getCompletedAt().toString(),
                            currentUser.displayName()));
        }
    }

    private WorkItem require(OrganizationContext context, UUID id) {
        return workItems
                .findByIdAndOrganizationId(id, context.organizationId())
                .orElseThrow(() -> new NotFoundException("WorkItem", id));
    }

    private static WorkCategory parseCategory(String raw) {
        try {
            return WorkCategory.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown work category: " + raw);
        }
    }

    private static WorkStatus parseStatus(String raw) {
        try {
            return WorkStatus.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown work status: " + raw);
        }
    }

    public record WorkCompletedPayload(
            UUID workItemId, String title, String category, String completedAt, String completedBy) {}
}
