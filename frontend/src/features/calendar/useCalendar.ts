import { useMemo } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiGet, apiSend, queryKeys } from '@/lib/api';

interface CalendarWire {
  periodMonth: string;
  dataSource: 'DEMO' | 'LIVE' | 'ERROR' | 'NOT_CONNECTED' | 'NEEDS_AUTHORIZATION';
  summary: { booked: number; attended: number; cancelled: number; noShow: number; rescheduled: number };
  days: {
    date: string;
    appointments: {
      id: string;
      time: string;
      label: string;
      serviceCategory: string | null;
      source: string | null;
      channelSourceId: string | null;
      status: string;
      durationMinutes: number;
    }[];
  }[];
  /** True for Vision, false for the practice. Only attribution is ever editable. */
  editable: boolean;
  channels: { id: string; name: string }[];
}

export interface CalendarAppointment {
  id: string;
  date: string;
  time: string;
  /** A first name and an initial. Never a full patient name. */
  patient: string;
  /** The channel credited, or null when genuinely unknown. */
  channelId: string | null;
  serviceInterest: string;
  source: string;
  status: string;
  duration: number;
}

/**
 * The Calendar's data.
 *
 * The API groups by day because which day an appointment falls on is a question about the
 * practice's timezone, and a browser in another zone would answer it differently. The screen wants
 * a flat list plus a lookup, so both are derived here from that grouping.
 */
export function useCalendar(orgId: string, month: string) {
  const query = useQuery({
    queryKey: queryKeys.calendar(orgId, month),
    queryFn: () => apiGet<CalendarWire>(`/orgs/${orgId}/calendar`, { month }),
    staleTime: 30_000,
  });

  const appointments = useMemo<CalendarAppointment[]>(
    () =>
      (query.data?.days ?? []).flatMap((day) =>
        day.appointments.map((a) => ({
          id: a.id,
          date: day.date,
          time: a.time,
          patient: a.label,
            channelId: a.channelSourceId ?? null,
          serviceInterest: a.serviceCategory ?? 'General',
          // Null means the channel is genuinely unknown; "Direct" would credit a channel that
          // earned nothing.
          source: a.source ?? 'Unattributed',
          status: a.status,
          duration: a.durationMinutes,
        })),
      ),
    [query.data],
  );

  const byDate = useMemo(() => {
    const map = new Map<string, CalendarAppointment[]>();
    appointments.forEach((a) => {
      const list = map.get(a.date) ?? [];
      list.push(a);
      map.set(a.date, list);
    });
    return map;
  }, [appointments]);

  return {
    ...query,
    appointments,
    summary: query.data?.summary,
    dataSource: query.data?.dataSource,
    editable: query.data?.editable ?? false,
    channels: query.data?.channels ?? [],
    appointmentsOn: (date: string) => byDate.get(date) ?? [],
  };
}

/**
 * Crediting a booking to a marketing channel. The only write this screen has.
 *
 * Rescheduling, cancelling and re-timing are not here and will not be: the practice's scheduling
 * system owns those, and VisionOne showing "cancelled" while a patient is still booked would be a
 * fact about someone's week rather than a stale cache.
 */
export function useAttribution(orgId: string, month: string) {
  const client = useQueryClient();

  return useMutation({
    mutationFn: ({ appointmentId, channelSourceId }: { appointmentId: string; channelSourceId: string | null }) =>
      apiSend<CalendarWire>(
        'PUT',
        `/orgs/${orgId}/admin/calendar/${appointmentId}/attribution`,
        { channelSourceId },
        { month },
      ),
    onSuccess: (fresh) => {
      // The write returns the refreshed month, so the panel updates from the write itself rather
      // than from a second request that could disagree with it.
      client.setQueryData(queryKeys.calendar(orgId, month), fresh);
      // Attribution moves cost per booked appointment, which both of these show.
      void client.invalidateQueries({ queryKey: ['overview', orgId] });
      void client.invalidateQueries({ queryKey: ['growth', orgId] });
    },
  });
}
