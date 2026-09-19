import { useLocation } from 'react-router-dom';
import { CLIENT_NAVIGATION } from './navigation';

/** Honest placeholder for the screens that arrive in Weeks 2 to 4. */
export function ComingSoon() {
  const { pathname } = useLocation();
  const slug = pathname.split('/').pop();
  const item = CLIENT_NAVIGATION.find((entry) => entry.to === slug);

  const week: Record<string, string> = {
    growth: 'Week 2',
    leads: 'Week 3',
    work: 'Week 3',
    'front-desk': 'Week 4',
    reports: 'Week 4',
  };

  return (
    <div className="rounded-lg border border-dashed border-border px-6 py-16 text-center">
      <h1 className="text-lg font-semibold">{item?.label ?? 'This screen'}</h1>
      <p className="mx-auto mt-2 max-w-md text-sm text-muted-foreground">
        Not built yet. Scheduled for {week[slug ?? ''] ?? 'a later increment'} of Phase 1.
      </p>
    </div>
  );
}
