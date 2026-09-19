import {
  Bar,
  BarChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import type { SourcePerformance } from '@/lib/types';

/**
 * Leads and booked appointments per channel.
 *
 * Colours come from the design tokens, never per-chart literals, so light and dark stay
 * consistent across every chart in the product.
 */
export function SourceBarChart({ sources }: { sources: SourcePerformance[] }) {
  const data = sources.map((source) => ({
    name: source.displayName,
    Leads: source.leads,
    Booked: source.booked,
  }));

  // The table below carries the same figures, so the chart is decorative to a screen reader -
  // but it still needs a name, and colour alone must not be the only cue. The legend supplies
  // the second cue; the table supplies the accessible reading.
  const summary = data
    .map((row) => `${row.name}: ${row.Leads} leads, ${row.Booked} booked`)
    .join('; ');

  return (
    <div
      className="h-64 w-full"
      role="img"
      aria-label={`Leads and booked appointments by channel. ${summary}.`}
    >
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 8, bottom: 8, left: -16 }}>
          <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" vertical={false} />
          <XAxis
            dataKey="name"
            tick={{ fontSize: 11, fill: 'hsl(var(--muted-foreground))' }}
            interval={0}
            angle={-18}
            textAnchor="end"
            height={58}
            stroke="hsl(var(--border))"
          />
          <YAxis
            tick={{ fontSize: 11, fill: 'hsl(var(--muted-foreground))' }}
            stroke="hsl(var(--border))"
            allowDecimals={false}
          />
          <Tooltip
            cursor={{ fill: 'hsl(var(--primary) / 0.06)' }}
            contentStyle={{
              backgroundColor: 'hsl(var(--card))',
              border: '1px solid hsl(var(--border))',
              borderRadius: '0.75rem',
              boxShadow: 'var(--shadow-lg)',
              fontSize: 12,
            }}
          />
          <Legend wrapperStyle={{ fontSize: 12 }} />
          {/* Fully rounded caps: a bar reads as a measured quantity, not a cut column. */}
          <Bar
            dataKey="Leads"
            fill="hsl(var(--chart-1))"
            radius={[999, 999, 999, 999]}
            maxBarSize={18}
          />
          <Bar
            dataKey="Booked"
            fill="hsl(var(--navy))"
            radius={[999, 999, 999, 999]}
            maxBarSize={18}
          />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
