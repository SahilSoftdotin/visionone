import { cn } from '@/lib/utils';

/**
 * Brand lockups.
 *
 * The Vision Digital Lab mark is the real asset from the agency's site and keeps its own
 * colours - a logo recoloured to match its host stops being the logo. The client mark is a
 * monogram placeholder: drop a real file into `public/brand/` and pass it as `logoSrc` when
 * THRIVE supply one.
 */

/** VisionOne wordmark. One text node, so no flex gap can creep between Vision and One. */
export function VisionOneMark({ className }: { className?: string }) {
  return (
    <span className={cn('text-base font-semibold tracking-[-0.015em]', className)}>
      <span>Vision</span>
      <span className="text-primary-text">One</span>
    </span>
  );
}

export function VisionDigitalLabMark({ className }: { className?: string }) {
  return (
    <img
      src="/brand/vision-digital-lab.svg"
      alt="Vision Digital Lab"
      className={cn('h-5 w-5 rounded-[5px]', className)}
    />
  );
}

/**
 * The client this workspace belongs to. Shown beside VisionOne so the connection between the
 * platform and the practice is visible on every screen.
 */
export function ClientMark({
  name,
  logoSrc,
  className,
}: {
  name: string;
  logoSrc?: string;
  className?: string;
}) {
  const monogram = name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((word) => word[0])
    .join('')
    .toUpperCase();

  return (
    <span className={cn('flex items-center gap-2', className)}>
      {logoSrc ? (
        <img src={logoSrc} alt={name} className="h-5 w-5 rounded-[5px] object-contain" />
      ) : (
        <span
          aria-hidden
          className="grid h-5 w-5 place-items-center rounded-[5px] bg-primary-soft text-[9px] font-bold tracking-tight text-primary-text ring-1 ring-inset ring-primary/20"
        >
          {monogram}
        </span>
      )}
      <span className="truncate text-sm font-medium">{name}</span>
    </span>
  );
}

/** "Part of Vision Digital Lab", said once, quietly, where it belongs. */
export function ParentBrandLine({ className }: { className?: string }) {
  return (
    <a
      href="https://visiondigitallab.com"
      target="_blank"
      rel="noreferrer"
      className={cn(
        'group flex items-center gap-2 rounded-md px-2 py-2 transition-colors hover:bg-muted',
        className,
      )}
    >
      <VisionDigitalLabMark className="h-6 w-6 shrink-0 transition-transform duration-200 group-hover:scale-105" />
      <span className="min-w-0 leading-tight">
        <span className="block text-[10px] font-semibold uppercase tracking-[0.1em] text-muted-foreground">
          Built by
        </span>
        <span className="block truncate text-xs font-semibold">Vision Digital Lab</span>
      </span>
    </a>
  );
}
