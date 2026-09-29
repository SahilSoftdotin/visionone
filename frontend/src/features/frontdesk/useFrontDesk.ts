import { useQuery } from '@tanstack/react-query';
import { apiGet, queryKeys } from '@/lib/api';
import type { CallSummary } from '@/lib/types';

/** What the API sends. The screen's own shape is flatter; the adapter below bridges them. */
interface FrontDeskWire {
  periodMonth: string;
  dataSource: 'DEMO' | 'LIVE' | 'ERROR' | 'NOT_CONNECTED' | 'NEEDS_AUTHORIZATION';
  summary: CallSummary;
  hourly: number[];
  /** Always the whole month, never the filtered list. */
  outcomeCounts: { outcome: string; count: number }[];
  recent: {
    id: string;
    callerLabel: string;
    handledBy: 'AI_FRONT_DESK' | 'PRACTICE_TEAM' | 'VOICEMAIL' | 'UNKNOWN';
    outcome: string;
    afterHours: boolean;
    durationSeconds: number;
    startedAt: string;
  }[];
}

const handlerLabels: Record<string, string> = {
  AI_FRONT_DESK: 'AI Front Desk',
  PRACTICE_TEAM: 'Practice Team',
  VOICEMAIL: 'Voicemail',
  UNKNOWN: 'Unknown',
};

/**
 * The Front Desk screen's data.
 *
 * The summary arrives nested and is flattened here, and the handler enum becomes the label the
 * screen already prints. Keeping that mapping in one place means the wire stays the database's
 * vocabulary and the screen stays the reader's.
 */
export function useFrontDesk(orgId: string, month: string, outcome: string | null = null) {
  const query = useQuery({
    queryKey: queryKeys.frontDesk(orgId, month, outcome),
    queryFn: () =>
      apiGet<FrontDeskWire>(`/orgs/${orgId}/frontdesk`, outcome ? { month, outcome } : { month }),
    staleTime: 30_000,
    // The tiles and the pills do not change when the filter does, so keeping the previous
    // response on screen while the table refetches avoids the whole screen flashing a skeleton.
    placeholderData: (previous) => previous,
  });

  const data = query.data
    ? {
        ...query.data.summary,
        dataSource: query.data.dataSource,
        hourly: query.data.hourly,
        outcomeCounts: query.data.outcomeCounts,
        recent: query.data.recent.map((call) => ({
          ...call,
          maskedNumber: call.callerLabel,
          handledBy: handlerLabels[call.handledBy] ?? call.handledBy,
        })),
      }
    : undefined;

  return { ...query, data };
}
