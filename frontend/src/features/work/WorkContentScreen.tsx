import { useState, type CSSProperties } from 'react';
import { AlertTriangle, Check, CircleDot, Clock, FileText, MessageSquareWarning, UserRoundCheck } from 'lucide-react';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import {
  contentDemo,
  workDemo,
  type ContentItemRow,
  type ContentStatus,
  type WorkStatus,
} from '@/lib/demoOperations';
import { formatCount } from '@/lib/format';
import { cn } from '@/lib/utils';

/**
 * Screen 5 - Work & Content, combined.
 *
 * One screen rather than separate Content, SEO, Reputation and project-management products. Work
 * items lead with the business reason, not the task, because a practice owner reads "why" before
 * "what". Content carries the two client actions Phase 1 supports: approve, or request changes.
 */

const workTone: Record<WorkStatus, 'neutral' | 'positive' | 'caution' | 'critical'> = {
  PLANNED: 'neutral',
  IN_PROGRESS: 'neutral',
  BLOCKED: 'critical',
  WAITING_FOR_CLIENT: 'caution',
  COMPLETED: 'positive',
  CANCELLED: 'neutral',
};

const contentTone: Record<ContentStatus, 'neutral' | 'positive' | 'caution' | 'critical'> = {
  IDEA: 'neutral',
  DRAFTING: 'neutral',
  INTERNAL_REVIEW: 'neutral',
  CLIENT_REVIEW: 'caution',
  CHANGES_REQUESTED: 'critical',
  APPROVED: 'positive',
  SCHEDULED: 'positive',
  PUBLISHED: 'positive',
};

const pretty = (s: string) => s.toLowerCase().replace(/_/g, ' ');

export function WorkContentScreen() {
  // Local only: no endpoint accepts an approval yet, so the change lives for this session.
  const [content, setContent] = useState<ContentItemRow[]>(contentDemo);

  const decide = (id: string, status: ContentStatus) =>
    setContent((rows) => rows.map((r) => (r.id === id ? { ...r, status } : r)));

  const waitingOnClient = workDemo.filter((w) => w.status === 'WAITING_FOR_CLIENT').length;
  const awaitingReview = content.filter((c) => c.status === 'CLIENT_REVIEW').length;

  return (
    <div className="space-y-6">
      <header className="reveal flex flex-wrap items-baseline justify-between gap-2">
        <div>
          <h1 className="text-xl font-semibold tracking-[-0.02em] sm:text-2xl">Work &amp; Content</h1>
          <p className="text-sm text-muted-foreground">What Vision is doing, and what needs you</p>
        </div>
        <Badge tone="demo">Demo data</Badge>
      </header>

      {/* What needs the client, first. Everything else is reporting. */}
      {(waitingOnClient > 0 || awaitingReview > 0) && (
        <section
          className="reveal relative overflow-hidden rounded-lg p-5"
          style={{ '--i': 1 } as CSSProperties}
        >
          <div className="flex flex-wrap items-center gap-4">
            <span className="grid h-11 w-11 shrink-0 place-items-center rounded-lg bg-caution/10 text-caution-text ring-1 ring-inset ring-caution/25">
              <UserRoundCheck className="h-5 w-5" aria-hidden />
            </span>
            <div className="min-w-0">
              <p className="text-sm font-semibold">Waiting for you</p>
              <p className="text-sm text-muted-foreground">
                {awaitingReview > 0 && (
                  <>
                    {formatCount(awaitingReview)} content{' '}
                    {awaitingReview === 1 ? 'item needs' : 'items need'} review
                  </>
                )}
                {awaitingReview > 0 && waitingOnClient > 0 && ' · '}
                {waitingOnClient > 0 && (
                  <>
                    {formatCount(waitingOnClient)} work{' '}
                    {waitingOnClient === 1 ? 'item is' : 'items are'} blocked on the practice
                  </>
                )}
              </p>
            </div>
          </div>
        </section>
      )}

      <Card className="reveal" style={{ '--i': 2 } as CSSProperties}>
        <CardHeader
          title="Work"
          action={
            <span className="text-xs text-muted-foreground">{formatCount(workDemo.length)} items</span>
          }
        />
        <CardBody className="space-y-3">
          {workDemo.map((w, i) => (
            <article
              key={w.id}
              className="reveal group rounded-lg border border-border/70 bg-card/60 p-4 transition-all duration-200 hover:border-primary/25 hover:elev-md"
              style={{ '--i': i } as CSSProperties}
            >
              <div className="flex flex-wrap items-start justify-between gap-2">
                <div className="min-w-0">
                  <div className="flex flex-wrap items-center gap-2">
                    <h3 className="font-semibold">{w.title}</h3>
                    <Badge tone={workTone[w.status]}>{pretty(w.status)}</Badge>
                  </div>
                  {/* The reason leads. A client cares why before what. */}
                  <p className="mt-1.5 text-sm text-muted-foreground">{w.businessReason}</p>
                </div>
                <span className="shrink-0 rounded-full bg-muted px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.06em] text-muted-foreground">
                  {pretty(w.category)}
                </span>
              </div>

              <p className="mt-3 flex items-start gap-2 text-sm">
                <CircleDot className="mt-0.5 h-3.5 w-3.5 shrink-0 text-primary-text" aria-hidden />
                <span>{w.clientUpdate}</span>
              </p>

              {w.clientDependency && (
                <p className="mt-2 flex items-start gap-2 rounded-md bg-caution/5 px-3 py-2 text-sm text-caution-text ring-1 ring-inset ring-caution/20">
                  <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden />
                  <span>{w.clientDependency}</span>
                </p>
              )}

              <p className="mt-3 flex items-center gap-1.5 text-xs text-muted-foreground">
                <Clock className="h-3 w-3" aria-hidden />
                Target {new Date(w.targetDate).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })}
                <span aria-hidden>·</span>
                {w.owner}
              </p>
            </article>
          ))}
        </CardBody>
      </Card>

      <Card className="reveal" style={{ '--i': 3 } as CSSProperties}>
        <CardHeader
          title="Content"
          action={<span className="text-xs text-muted-foreground">{formatCount(content.length)} items</span>}
        />
        <CardBody className="space-y-3">
          {content.map((c, i) => {
            const needsDecision = c.status === 'CLIENT_REVIEW';
            return (
              <article
                key={c.id}
                className={cn(
                  'reveal rounded-lg border p-4 transition-all duration-200',
                  needsDecision
                    ? 'border-caution/30 bg-caution/5'
                    : 'border-border/70 bg-card/60 hover:border-primary/25 hover:elev-md',
                )}
                style={{ '--i': i } as CSSProperties}
              >
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div className="min-w-0">
                    <div className="flex flex-wrap items-center gap-2">
                      <FileText className="h-4 w-4 shrink-0 text-muted-foreground" aria-hidden />
                      <h3 className="font-semibold">{c.title}</h3>
                      <Badge tone={contentTone[c.status]}>{pretty(c.status)}</Badge>
                    </div>
                    <p className="mt-1.5 text-sm text-muted-foreground">{c.summary}</p>
                  </div>
                  <span className="shrink-0 rounded-full bg-muted px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.06em] text-muted-foreground">
                    {pretty(c.type)}
                  </span>
                </div>

                {needsDecision && (
                  <div className="mt-3 flex flex-wrap gap-2 border-t border-caution/20 pt-3">
                    <button
                      type="button"
                      onClick={() => decide(c.id, 'APPROVED')}
                      className="inline-flex min-h-11 items-center gap-1.5 rounded-full bg-primary px-4 text-sm font-semibold text-primary-foreground transition-all duration-150 hover:-translate-y-px hover:elev-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                    >
                      <Check className="h-4 w-4" aria-hidden />
                      Approve
                    </button>
                    <button
                      type="button"
                      onClick={() => decide(c.id, 'CHANGES_REQUESTED')}
                      className="inline-flex min-h-11 items-center gap-1.5 rounded-full border border-border bg-card px-4 text-sm font-semibold transition-colors hover:border-critical/40 hover:bg-critical/5 hover:text-critical-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                    >
                      <MessageSquareWarning className="h-4 w-4" aria-hidden />
                      Request changes
                    </button>
                  </div>
                )}
              </article>
            );
          })}
        </CardBody>
      </Card>

      <p className="reveal text-xs text-muted-foreground" style={{ '--i': 4 } as CSSProperties}>
        Approvals are not saved yet: no endpoint accepts them, so a decision made here lasts only
        for this session. Persisting it is the Week-3 backend increment.
      </p>
    </div>
  );
}
