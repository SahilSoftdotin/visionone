import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, apiGet, apiSend, queryKeys } from '@/lib/api';
import type { MonthlyReportResponse, ReportListResponse } from '@/lib/types';

/** The list of reports, for the month selector. Newest first. */
export function useReportList(orgId: string) {
  return useQuery({
    queryKey: queryKeys.reports(orgId),
    queryFn: () => apiGet<ReportListResponse>(`/orgs/${orgId}/reports`),
    staleTime: 60_000,
  });
}

/**
 * One report: the chosen one, or the most recent if none is chosen.
 *
 * <p>Two endpoints behind one hook because the screen has one job. Opening on the latest rather
 * than on the current month is deliberate - on the second of the month there is usually no report
 * yet, and an empty screen would be a worse answer than last month's report.
 *
 * <p>A 404 is a real answer here, not a failure: a practice in its first month has no report. It is
 * surfaced as {@code isMissing} so the screen can say so plainly instead of showing a retry button
 * for something retrying will not fix.
 */
export function useReport(orgId: string, reportId?: string) {
  const query = useQuery({
    queryKey: reportId ? queryKeys.report(orgId, reportId) : queryKeys.latestReport(orgId),
    queryFn: () =>
      apiGet<MonthlyReportResponse>(
        reportId ? `/orgs/${orgId}/reports/${reportId}` : `/orgs/${orgId}/reports/latest`,
      ),
    staleTime: 60_000,
    retry: (failureCount, error) =>
      !(error instanceof ApiError && error.status === 404) && failureCount < 2,
  });

  const isMissing = query.error instanceof ApiError && query.error.status === 404;

  return { ...query, isMissing, error: isMissing ? null : query.error };
}

export interface NarrativeInput {
  keyLearning: string;
  nextActions: string;
  decisionsRequired: string;
}

/**
 * Vision Digital Lab's three report actions.
 *
 * Every one returns the refreshed report, which goes straight into the cache under its id and as
 * the latest if it is the newest - so the screen updates from the write itself, without a refetch
 * that could briefly show the old figures.
 */
export function useReportAdmin(orgId: string) {
  const client = useQueryClient();

  const onSaved = (fresh: MonthlyReportResponse) => {
    client.setQueryData(queryKeys.report(orgId, fresh.id), fresh);
    void client.invalidateQueries({ queryKey: queryKeys.reports(orgId) });
    void client.invalidateQueries({ queryKey: queryKeys.latestReport(orgId) });
  };

  const generate = useMutation({
    mutationFn: (month: string) =>
      apiSend<MonthlyReportResponse>('POST', `/orgs/${orgId}/admin/reports/generate`, undefined, {
        month,
      }),
    onSuccess: onSaved,
  });

  const writeNarrative = useMutation({
    mutationFn: ({ reportId, ...body }: NarrativeInput & { reportId: string }) =>
      apiSend<MonthlyReportResponse>('PUT', `/orgs/${orgId}/admin/reports/${reportId}/narrative`, body),
    onSuccess: onSaved,
  });

  const share = useMutation({
    mutationFn: (reportId: string) =>
      apiSend<MonthlyReportResponse>('POST', `/orgs/${orgId}/admin/reports/${reportId}/share`),
    onSuccess: onSaved,
  });

  return { generate, writeNarrative, share };
}
