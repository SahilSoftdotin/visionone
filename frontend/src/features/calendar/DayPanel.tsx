import { X } from 'lucide-react';
import { ApiError } from '@/lib/api';
import { cn } from '@/lib/utils';
import { useAttribution, type CalendarAppointment } from './useCalendar';

interface Props {
  orgId: string;
  month: string;
  date: string;
  appointments: CalendarAppointment[];
  /** True for Vision. The practice reads this panel and changes nothing in it. */
  editable: boolean;
  channels: { id: string; name: string }[];
  onClose: () => void;
}

const statusTone: Record<string, string> = {
  BOOKED: 'bg-primary-soft text-primary-text',
  ATTENDED: 'bg-positive-soft text-positive-text',
  CANCELLED: 'bg-caution-soft text-caution-text',
  NO_SHOW: 'bg-critical-soft text-critical-text',
  RESCHEDULED: 'bg-caution-soft text-caution-text',
};

/**
 * One day's appointments.
 *
 * Read-only about the appointment itself, by design rather than by omission. When it is, how long
 * it runs, whether it happened and who it is with all belong to the practice's scheduling system;
 * VisionOne holds a reference to the booking, not the booking. Showing "cancelled" here while a
 * patient is still booked would be a fact about someone's week rather than a stale cache.
 *
 * The one exception is which marketing channel earned the booking. Healthie neither knows nor
 * cares which advertisement produced a patient, so that is VisionOne's own data, and correcting it
 * is Vision's own job. The practice sees it as text.
 *
 * Nothing here fetches anything further about the patient. The label is a first name and an
 * initial, which is all the API will ever hand over.
 */
export function DayPanel({
  orgId,
  month,
  date,
  appointments,
  editable,
  channels,
  onClose,
}: Props) {
  const attribute = useAttribution(orgId, month);
  const error = attribute.error as ApiError | null;

  const heading = new Date(`${date}T00:00:00`).toLocaleDateString(undefined, {
    weekday: 'long',
    day: 'numeric',
    month: 'long',
  });

  return (
    <div className="fixed inset-0 z-40 flex justify-end" role="dialog" aria-label="Appointments">
      <button
        type="button"
        aria-label="Close appointments"
        className="flex-1 bg-foreground/20"
        onClick={onClose}
      />
      <aside className="flex h-full w-full max-w-md flex-col overflow-y-auto border-l border-border bg-card">
        <header className="flex items-start justify-between gap-3 border-b border-border px-5 py-4">
          <div>
            <h2 className="text-base font-semibold">{heading}</h2>
            <p className="text-xs text-muted-foreground">
              {appointments.length} {appointments.length === 1 ? 'appointment' : 'appointments'}
            </p>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="Close"
            className="grid h-8 w-8 shrink-0 place-items-center rounded-md text-muted-foreground hover:bg-muted"
          >
            <X className="h-4 w-4" aria-hidden />
          </button>
        </header>

        {error && (
          <p role="alert" className="mx-5 mt-4 rounded-md bg-critical-soft px-3 py-2 text-xs text-critical-text">
            {error.message}
          </p>
        )}

        <ul className="flex-1 space-y-3 px-5 py-4">
          {appointments.map((a) => (
            <li key={a.id} className="rounded-lg border border-border/70 p-3">
              <div className="flex items-baseline justify-between gap-2">
                <span className="text-sm font-semibold tabular-nums">{a.time}</span>
                <span
                  className={cn(
                    'rounded-full px-2 py-0.5 text-[11px] font-medium',
                    statusTone[a.status] ?? 'bg-muted text-muted-foreground',
                  )}
                >
                  {a.status.toLowerCase().replace(/_/g, ' ')}
                </span>
              </div>

              <p className="mt-1 text-sm font-semibold">{a.patient}</p>
              <p className="text-xs text-muted-foreground">
                {a.serviceInterest} · {a.duration} min
              </p>

              <div className="mt-3 border-t border-border/60 pt-3">
                <label
                  className="text-[11px] font-semibold uppercase tracking-wide text-muted-foreground"
                  htmlFor={editable ? `channel-${a.id}` : undefined}
                >
                  Credited to
                </label>
                {editable ? (
                  <select
                    id={`channel-${a.id}`}
                    value={a.channelId ?? ''}
                    disabled={attribute.isPending}
                    onChange={(event) =>
                      attribute.mutate({
                        appointmentId: a.id,
                        channelSourceId: event.target.value === '' ? null : event.target.value,
                      })
                    }
                    className="mt-1 h-9 w-full rounded-md border border-border bg-card px-2 text-sm disabled:opacity-60"
                  >
                    {/* Unattributed is a real answer, not a blank. A guess would credit a channel
                        that earned nothing, and cost per booked appointment is built on these. */}
                    <option value="">Unattributed</option>
                    {channels.map((c) => (
                      <option key={c.id} value={c.id}>
                        {c.name}
                      </option>
                    ))}
                  </select>
                ) : (
                  <p className="mt-1 text-sm">{a.source}</p>
                )}
              </div>
            </li>
          ))}
        </ul>

        <footer className="border-t border-border px-5 py-3">
          <p className="text-[11px] text-muted-foreground">
            Times, duration and status are managed in the practice&rsquo;s scheduling system.
            VisionOne holds a reference to each booking, not the booking itself
            {editable ? ', and records which channel earned it.' : '.'}
          </p>
        </footer>
      </aside>
    </div>
  );
}
