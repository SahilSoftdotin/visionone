import type { TooltipProps } from 'recharts';

/**
 * One tooltip for every chart in the product.
 *
 * Recharts' default is unstyled and ignores the theme. This reads the same tokens as everything
 * else, so a tooltip in the donut looks like a tooltip in a bar chart, and both survive a theme
 * change without being edited.
 */
export function ChartTooltip({
  active,
  payload,
  label,
  formatter,
  labelFormatter,
}: TooltipProps<number, string> & {
  formatter?: (value: number, name: string) => string;
  labelFormatter?: (label: unknown) => string;
}) {
  if (!active || !payload || payload.length === 0) return null;

  const heading = labelFormatter ? labelFormatter(label) : (label as string);

  return (
    <div className="rounded-lg border border-border bg-card px-3 py-2 elev-lg">
      {heading !== undefined && heading !== '' && (
        <p className="mb-1 text-xs font-semibold text-foreground">{heading}</p>
      )}
      <ul className="space-y-0.5">
        {payload.map((entry, i) => (
          <li key={i} className="flex items-center gap-2 text-xs">
            <span
              className="h-2 w-2 shrink-0 rounded-full"
              style={{ background: entry.color ?? entry.payload?.fill }}
              aria-hidden
            />
            <span className="text-muted-foreground">{entry.name}</span>
            <span className="ml-auto font-semibold tabular-nums text-foreground">
              {formatter
                ? formatter(Number(entry.value), String(entry.name))
                : Number(entry.value).toLocaleString()}
            </span>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** The faint block Recharts paints behind the hovered category. */
export const hoverCursor = { fill: 'hsl(var(--primary) / 0.06)' };
