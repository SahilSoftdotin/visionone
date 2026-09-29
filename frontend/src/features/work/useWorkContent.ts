import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiGet, apiSend, queryKeys } from '@/lib/api';
import type {
  ContentListResponse,
  ContentStatus,
  ContentType,
  WorkCategory,
  WorkListResponse,
  WorkStatus,
} from '@/lib/types';

/**
 * Two panels, two queries.
 *
 * Work and content are separate modules with separate endpoints; combining them server-side would
 * mean one module reaching into the other's tables for no gain. Each panel also loads and refreshes
 * on its own, which is what you want when only one of them just changed.
 */
export function useWork(orgId: string) {
  return useQuery({
    queryKey: queryKeys.work(orgId),
    queryFn: () => apiGet<WorkListResponse>(`/orgs/${orgId}/work`),
    staleTime: 30_000,
  });
}

export function useContent(orgId: string) {
  return useQuery({
    queryKey: queryKeys.content(orgId),
    queryFn: () => apiGet<ContentListResponse>(`/orgs/${orgId}/content`),
    staleTime: 30_000,
  });
}

/** The client's two decisions. Both return the refreshed list, so the panel updates from the write. */
export function useContentDecision(orgId: string) {
  const client = useQueryClient();

  const onDecided = (fresh: ContentListResponse) => {
    client.setQueryData(queryKeys.content(orgId), fresh);
    // Content published and approved both feed the monthly report and the Overview activity counts.
    void client.invalidateQueries({ queryKey: ['overview', orgId] });
  };

  const approve = useMutation({
    mutationFn: (contentItemId: string) =>
      apiSend<ContentListResponse>('POST', `/orgs/${orgId}/content/${contentItemId}/approve`),
    onSuccess: onDecided,
  });

  const requestChanges = useMutation({
    mutationFn: ({ contentItemId, feedback }: { contentItemId: string; feedback: string }) =>
      apiSend<ContentListResponse>(
        'POST',
        `/orgs/${orgId}/content/${contentItemId}/request-changes`,
        { feedback },
      ),
    onSuccess: onDecided,
  });

  return { approve, requestChanges };
}

/** Vision moving its own work along. */
export function useWorkTransition(orgId: string) {
  const client = useQueryClient();

  return useMutation({
    mutationFn: ({ workItemId, toStatus }: { workItemId: string; toStatus: WorkStatus }) =>
      apiSend<WorkListResponse>('POST', `/orgs/${orgId}/admin/work/${workItemId}/status`, { toStatus }),
    onSuccess: (fresh) => {
      client.setQueryData(queryKeys.work(orgId), fresh);
      void client.invalidateQueries({ queryKey: ['overview', orgId] });
    },
  });
}

/** The fields Vision writes on a work item. Mirrors WorkWriteRequests.UpsertWork. */
export interface WorkInput {
  title: string;
  category: WorkCategory;
  businessReason: string;
  owner: string;
  targetDate: string | null;
  clientUpdate: string | null;
}

/** The fields Vision writes on a content item. Mirrors ContentWriteRequests.UpsertContent. */
export interface ContentInput {
  title: string;
  summary: string | null;
  contentType: ContentType;
  author: string;
  draftUrl: string | null;
  publishedUrl: string | null;
}

/**
 * Vision creating and editing work items. Moving them is {@link useWorkTransition}.
 * Every write returns the refreshed list, so the panel updates from the write itself.
 */
export function useWorkAdmin(orgId: string) {
  const client = useQueryClient();
  const onSaved = (fresh: WorkListResponse) => {
    client.setQueryData(queryKeys.work(orgId), fresh);
    void client.invalidateQueries({ queryKey: ['overview', orgId] });
  };

  const create = useMutation({
    mutationFn: (input: WorkInput) =>
      apiSend<WorkListResponse>('POST', `/orgs/${orgId}/admin/work`, input),
    onSuccess: onSaved,
  });

  const update = useMutation({
    mutationFn: ({ id, ...input }: WorkInput & { id: string }) =>
      apiSend<WorkListResponse>('PUT', `/orgs/${orgId}/admin/work/${id}`, input),
    onSuccess: onSaved,
  });

  return { create, update };
}

/** Vision authoring content and moving it through its own stages. */
export function useContentAdmin(orgId: string) {
  const client = useQueryClient();
  const onSaved = (fresh: ContentListResponse) => {
    client.setQueryData(queryKeys.content(orgId), fresh);
    void client.invalidateQueries({ queryKey: ['overview', orgId] });
  };

  const create = useMutation({
    mutationFn: (input: ContentInput) =>
      apiSend<ContentListResponse>('POST', `/orgs/${orgId}/admin/content`, input),
    onSuccess: onSaved,
  });

  const update = useMutation({
    mutationFn: ({ id, ...input }: ContentInput & { id: string }) =>
      apiSend<ContentListResponse>('PUT', `/orgs/${orgId}/admin/content/${id}`, input),
    onSuccess: onSaved,
  });

  const move = useMutation({
    mutationFn: ({ id, toStatus }: { id: string; toStatus: ContentStatus }) =>
      apiSend<ContentListResponse>('POST', `/orgs/${orgId}/admin/content/${id}/status`, { toStatus }),
    onSuccess: onSaved,
  });

  return { create, update, move };
}
