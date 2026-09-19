import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import type { Recommendation } from '@/lib/types';

const FIELDS = [
  { key: 'observation', label: 'What we see' },
  { key: 'proposedAction', label: 'What we propose' },
  { key: 'rationale', label: 'Why' },
] as const;

/**
 * The month's recommendation.
 *
 * The expected effect is labelled a hypothesis in the markup itself, not in a caption that can be
 * dropped in a redesign. VisionOne never displays a guaranteed result.
 */
export function RecommendationCard({ recommendation }: { recommendation: Recommendation }) {
  return (
    <Card>
      <CardHeader
        title="What we recommend next"
        action={
          <Badge tone={recommendation.status === 'OPEN' ? 'caution' : 'positive'}>
            {recommendation.status === 'OPEN' ? 'Awaiting your decision' : recommendation.status}
          </Badge>
        }
      />
      <CardBody className="space-y-4">
        {FIELDS.map(({ key, label }) => (
          <div key={key}>
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">{label}</p>
            <p className="mt-1 text-sm leading-relaxed">{recommendation[key]}</p>
          </div>
        ))}

        <div className="rounded-md bg-muted/60 p-3">
          <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
            Expected effect <span className="normal-case italic">(hypothesis, not a guarantee)</span>
          </p>
          <p className="mt-1 text-sm leading-relaxed">{recommendation.expectedEffect}</p>
        </div>

        <div className="rounded-md border border-caution/30 bg-caution/5 p-3">
          <p className="text-xs font-medium uppercase tracking-wide text-caution">Decision required</p>
          <p className="mt-1 text-sm leading-relaxed">{recommendation.decisionRequired}</p>
        </div>
      </CardBody>
    </Card>
  );
}
