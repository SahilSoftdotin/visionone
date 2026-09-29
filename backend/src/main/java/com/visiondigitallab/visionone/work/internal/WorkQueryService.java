package com.visiondigitallab.visionone.work.internal;

import com.visiondigitallab.visionone.tenant.api.OrganizationContext;
import com.visiondigitallab.visionone.work.api.WorkListResponse;
import com.visiondigitallab.visionone.work.domain.WorkItem;
import com.visiondigitallab.visionone.work.domain.WorkStatus;
import com.visiondigitallab.visionone.work.repository.WorkItemRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads for the work panel.
 *
 * <p>Current state, not a period: "what needs my attention" is a question about now. Only the
 * completed count is windowed, and that lives on the Overview.
 */
@Service
@Transactional(readOnly = true)
public class WorkQueryService {

    private final WorkItemRepository workItems;

    public WorkQueryService(WorkItemRepository workItems) {
        this.workItems = workItems;
    }

    public WorkListResponse list(OrganizationContext context) {
        List<WorkItem> all = workItems.findByOrganizationIdOrderByCreatedAtDesc(context.organizationId());

        List<WorkListResponse.WorkItemRow> rows = all.stream().map(WorkQueryService::toRow).toList();

        WorkListResponse.Summary summary = new WorkListResponse.Summary(
                count(all, WorkStatus.COMPLETED),
                count(all, WorkStatus.IN_PROGRESS),
                count(all, WorkStatus.BLOCKED),
                count(all, WorkStatus.WAITING_FOR_CLIENT));

        return new WorkListResponse(summary, rows, context.isVisionAdmin());
    }

    private static long count(List<WorkItem> items, WorkStatus status) {
        return items.stream().filter(item -> item.getStatus() == status).count();
    }

    private static WorkListResponse.WorkItemRow toRow(WorkItem item) {
        return new WorkListResponse.WorkItemRow(
                item.getId(),
                item.getTitle(),
                item.getCategory().name(),
                item.getStatus().name(),
                item.getBusinessReason(),
                item.getOwnerName(),
                item.getTargetDate(),
                item.isClientDependency(),
                item.getClientVisibleUpdate(),
                item.getCompletedAt() == null ? null : item.getCompletedAt().toString());
    }
}
