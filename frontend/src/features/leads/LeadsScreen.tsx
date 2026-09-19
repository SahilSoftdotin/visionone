import { useMemo, useState } from 'react';
import {
  flexRender,
  getCoreRowModel,
  getFilteredRowModel,
  getSortedRowModel,
  useReactTable,
  type ColumnDef,
  type SortingState,
} from '@tanstack/react-table';
import { ArrowUpDown, Clock, Search, UserCheck, Users, Zap } from 'lucide-react';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { KpiTile, TILE_TONES } from '@/components/KpiTile';
import { LEAD_STATUSES, leadResponseSummary, leadsDemo, type LeadRow, type LeadStatus } from '@/lib/demoData';
import { formatCount } from '@/lib/format';
import { cn } from '@/lib/utils';

/**
 * Screen 3 - Leads.
 *
 * A lightweight pipeline: filter, view, and see what happened. Deliberately not a CRM - no
 * sequences, no automation, no clinical intake. Service interest is a broad category only.
 *
 * Every row here is synthetic. See lib/demoData.ts.
 */

const statusTone: Record<LeadStatus, 'neutral' | 'positive' | 'caution' | 'critical'> = {
  NEW: 'caution',
  CONTACTED: 'neutral',
  QUALIFIED: 'neutral',
  APPOINTMENT_REQUESTED: 'caution',
  BOOKED: 'positive',
  ATTENDED: 'positive',
  NOT_CONVERTED: 'critical',
  DUPLICATE: 'neutral',
};

const prettyStatus = (s: LeadStatus) => s.toLowerCase().replace(/_/g, ' ');

export function LeadsScreen() {
  const [sorting, setSorting] = useState<SortingState>([{ id: 'createdAt', desc: true }]);
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState<LeadStatus | 'ALL'>('ALL');

  const rows = useMemo(
    () => (status === 'ALL' ? leadsDemo : leadsDemo.filter((l) => l.status === status)),
    [status],
  );

  const columns = useMemo<ColumnDef<LeadRow>[]>(
    () => [
      {
        accessorKey: 'id',
        header: 'Lead',
        cell: (c) => (
          <span className="font-mono text-xs font-medium text-muted-foreground">
            {c.getValue<string>()}
          </span>
        ),
      },
      {
        accessorKey: 'name',
        header: 'Name',
        cell: (c) => <span className="font-medium">{c.getValue<string>()}</span>,
      },
      { accessorKey: 'serviceInterest', header: 'Interest' },
      { accessorKey: 'source', header: 'Source' },
      {
        accessorKey: 'campaign',
        header: 'Campaign',
        cell: (c) => (
          <span className="text-muted-foreground">{c.getValue<string | null>() ?? '—'}</span>
        ),
      },
      {
        accessorKey: 'createdAt',
        header: 'Created',
        cell: (c) =>
          new Date(c.getValue<string>()).toLocaleDateString(undefined, {
            month: 'short',
            day: 'numeric',
          }),
      },
      {
        accessorKey: 'responseMinutes',
        header: 'Response',
        cell: (c) => {
          const m = c.getValue<number | null>();
          if (m === null) {
            return <span className="text-critical-text">never</span>;
          }
          return (
            <span className={cn('tabular-nums', m <= 15 ? 'text-positive-text' : 'text-caution-text')}>
              {m < 60 ? `${m}m` : `${Math.round(m / 60)}h`}
            </span>
          );
        },
      },
      {
        accessorKey: 'status',
        header: 'Status',
        cell: (c) => {
          const s = c.getValue<LeadStatus>();
          return <Badge tone={statusTone[s]}>{prettyStatus(s)}</Badge>;
        },
      },
      { accessorKey: 'owner', header: 'Owner' },
    ],
    [],
  );

  const table = useReactTable({
    data: rows,
    columns,
    state: { sorting, globalFilter: query },
    onSortingChange: setSorting,
    onGlobalFilterChange: setQuery,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
  });

  const booked = leadsDemo.filter((l) => l.booked).length;
  const qualified = leadsDemo.filter((l) =>
    ['QUALIFIED', 'APPOINTMENT_REQUESTED', 'BOOKED', 'ATTENDED'].includes(l.status),
  ).length;

  return (
    <div className="space-y-6">
      <header className="reveal flex flex-wrap items-baseline justify-between gap-2">
        <div>
          <h1 className="text-xl font-semibold tracking-[-0.02em] sm:text-2xl">Leads</h1>
          <p className="text-sm text-muted-foreground">
            {formatCount(leadsDemo.length)} enquiries this period
          </p>
        </div>
        <Badge tone="demo">Demo data</Badge>
      </header>

      <section aria-label="Lead summary" className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {[
          { label: 'Total leads', value: formatCount(leadsDemo.length), icon: Users, hint: 'this period' },
          { label: 'Qualified', value: formatCount(qualified), icon: UserCheck, hint: 'reached qualified+' },
          { label: 'Booked', value: formatCount(booked), icon: Zap, hint: 'appointments made' },
          {
            label: 'Median response',
            value: `${leadResponseSummary.medianMinutes}m`,
            icon: Clock,
            hint: `${leadResponseSummary.underFifteenPercent}% under 15m`,
          },
        ].map((t, i) => (
          <div key={t.label} className="reveal" style={{ '--i': i + 1 } as React.CSSProperties}>
            <KpiTile label={t.label} value={t.value} icon={t.icon} hint={t.hint} tone={TILE_TONES[i % TILE_TONES.length]} />
          </div>
        ))}
      </section>

      <Card className="reveal" style={{ '--i': 5 } as React.CSSProperties}>
        <CardHeader
          title="Pipeline"
          action={
            <span className="text-xs text-muted-foreground">
              {formatCount(table.getFilteredRowModel().rows.length)} shown
            </span>
          }
        />
        <CardBody className="space-y-4 px-0 pb-0">
          <div className="flex flex-wrap items-center gap-2 px-5 sm:px-6">
            <label className="relative flex-1 min-w-[12rem]">
              <span className="sr-only">Search leads</span>
              <Search
                className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground"
                aria-hidden
              />
              <input
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search name, source, campaign…"
                className="h-11 w-full rounded-full border border-border bg-card pl-9 pr-3 text-sm outline-none transition-shadow placeholder:text-muted-foreground focus:border-primary focus:ring-4 focus:ring-primary/15"
              />
            </label>
          </div>

          {/* Status filter. Horizontal scroll rather than wrap, so the row height is stable. */}
          <div className="flex gap-1.5 overflow-x-auto px-5 pb-1 sm:px-6">
            {(['ALL', ...LEAD_STATUSES] as const).map((s) => {
              const active = status === s;
              const count = s === 'ALL' ? leadsDemo.length : leadsDemo.filter((l) => l.status === s).length;
              return (
                <button
                  key={s}
                  type="button"
                  onClick={() => setStatus(s)}
                  aria-pressed={active}
                  className={cn(
                    'shrink-0 rounded-full px-3 py-1.5 text-xs font-semibold capitalize transition-all duration-150',
                    active
                      ? 'bg-primary text-primary-foreground elev-sm'
                      : 'bg-muted text-muted-foreground hover:bg-primary-soft hover:text-primary-text',
                  )}
                >
                  {s === 'ALL' ? 'All' : prettyStatus(s)}
                  <span className="ml-1.5 tabular-nums opacity-70">{count}</span>
                </button>
              );
            })}
          </div>

          <div className="overflow-x-auto">
            <table className="w-full min-w-[900px] text-sm">
              <caption className="sr-only">Lead pipeline with status, source and response time</caption>
              <thead>
                {table.getHeaderGroups().map((hg) => (
                  <tr
                    key={hg.id}
                    className="border-y border-border text-left text-xs uppercase tracking-wide text-muted-foreground"
                  >
                    {hg.headers.map((h) => (
                      <th key={h.id} scope="col" className="px-3 py-3 font-semibold first:pl-5 last:pr-5 sm:first:pl-6 sm:last:pr-6">
                        <button
                          type="button"
                          onClick={h.column.getToggleSortingHandler()}
                          className="inline-flex items-center gap-1 uppercase tracking-wide transition-colors hover:text-foreground"
                        >
                          {flexRender(h.column.columnDef.header, h.getContext())}
                          <ArrowUpDown className="h-3 w-3 opacity-50" aria-hidden />
                        </button>
                      </th>
                    ))}
                  </tr>
                ))}
              </thead>
              <tbody>
                {table.getRowModel().rows.map((row, i) => (
                  <tr
                    key={row.id}
                    className="reveal border-b border-border last:border-0 transition-colors hover:bg-primary/[0.04]"
                    style={{ '--i': Math.min(i, 10) } as React.CSSProperties}
                  >
                    {row.getVisibleCells().map((cell) => (
                      <td key={cell.id} className="px-3 py-3 first:pl-5 last:pr-5 sm:first:pl-6 sm:last:pr-6">
                        {flexRender(cell.column.columnDef.cell, cell.getContext())}
                      </td>
                    ))}
                  </tr>
                ))}
                {table.getRowModel().rows.length === 0 && (
                  <tr>
                    <td colSpan={columns.length} className="px-6 py-12 text-center text-sm text-muted-foreground">
                      No leads match that filter.
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </CardBody>
      </Card>

      <p className="reveal text-xs text-muted-foreground" style={{ '--i': 6 } as React.CSSProperties}>
        Names are synthetic and service interest is a broad category. VisionOne is a growth
        platform, not an EHR: no medical history, diagnoses, labs or notes are stored.
      </p>
    </div>
  );
}
