import { useState, type CSSProperties } from 'react';
import { useParams } from 'react-router-dom';
import {
  AlertTriangle,
  Check,
  CircleDot,
  Clock,
  FileText,
  MessageSquareWarning,
  Pencil,
  Plus,
  UserRoundCheck,
} from 'lucide-react';
import { Card, CardBody, CardHeader } from '@/components/ui/Card';
import { Badge } from '@/components/ui/Badge';
import { DataStateBoundary } from '@/components/ui/DataStateBoundary';
import { useRowFocus } from '@/lib/useRowFocus';
import { useUrlFilter } from '@/lib/useUrlFilter';
import { Skeleton } from '@/components/ui/Skeleton';
import type {
  ContentListResponse,
  ContentStatus,
  WorkListResponse,
  WorkStatus,
} from '@/lib/types';
import type { ApiError } from '@/lib/api';
import { formatCount } from '@/lib/format';
import { cn } from '@/lib/utils';
import {
  useContent,
  useContentAdmin,
  useContentDecision,
  useWork,
  useWorkAdmin,
  useWorkTransition,
} from './useWorkContent';
import { StatusFilter } from '@/components/ui/StatusFilter';
import { WorkForm } from './WorkForm';
import { ContentForm } from './ContentForm';
import { secondaryButton } from './formStyles';

/**
 * Screen 5 - Work & Content, combined.
 *
 * One screen rather than separate Content, SEO, Reputation and project-management products. Work
 * items lead with the business reason, not the task, because a practice owner reads "why" before
 * "what".
 *
 * Two audiences, one screen. The practice gets its two decisions on content - approve, or request
 * changes - and nothing else. Vision gets authoring: create, edit, and move items through its own
 * stages. Which controls appear is decided by the API ({@code editable}, {@code canDecide},
 * {@code nextStatuses}); the endpoints enforce the same rules regardless.
 */

const WORK_ORDER: readonly WorkStatus[] = [
  'PLANNED',
  'IN_PROGRESS',
  'BLOCKED',
  'WAITING_FOR_CLIENT',
  'COMPLETED',
  'CANCELLED',
];

const CONTENT_ORDER: readonly ContentStatus[] = [
  'IDEA',
  'DRAFTING',
  'INTERNAL_REVIEW',
  'CLIENT_REVIEW',
  'CHANGES_REQUESTED',
  'APPROVED',
  'SCHEDULED',
  'PUBLISHED',
];

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

/**
 * What each move button says. Worded from where the item is going, in Vision's language. APPROVED
 * is only ever reachable by Vision from SCHEDULED, so for Vision it means "unschedule".
 */
const moveLabel: Record<ContentStatus, string> = {
  IDEA: 'Back to idea',
  DRAFTING: 'Back to drafting',
  INTERNAL_REVIEW: 'Send to internal review',
  CLIENT_REVIEW: 'Send to the practice for review',
  CHANGES_REQUESTED: 'Changes requested',
  APPROVED: 'Unschedule',
  SCHEDULED: 'Schedule',
  PUBLISHED: 'Mark published',
};

const pretty = (s: string) => s.toLowerCase().replace(/_/g, ' ');

function countBy<S extends string>(statuses: S[]): Partial<Record<S, number>> {
  const out: Partial<Record<S, number>> = {};
  for (const s of statuses) out[s] = (out[s] ?? 0) + 1;
  return out;
}

export function WorkContentScreen() {
  const { orgId = '' } = useParams();
  const work = useWork(orgId);
  const contentQuery = useContent(orgId);

  return (
    <DataStateBoundary
      isLoading={work.isLoading || contentQuery.isLoading}
      error={work.error ?? contentQuery.error}
      skeleton={<WorkContentSkeleton />}
      onRetry={() => {
        void work.refetch();
        void contentQuery.refetch();
      }}
    >
      {work.data && contentQuery.data && (
        <WorkContentView orgId={orgId} work={work.data} content={contentQuery.data} />
      )}
    </DataStateBoundary>
  );
}

function WorkContentSkeleton() {
  return (
    <div className="space-y-6">
      <Skeleton className="h-9 w-48" />
      <Skeleton className="h-20" />
      <Skeleton className="h-64" />
      <Skeleton className="h-64" />
    </div>
  );
}

function WorkContentView({
  orgId,
  work,
  content,
}: {
  orgId: string;
  work: WorkListResponse;
  content: ContentListResponse;
}) {
  const waitingOnClient = work.summary.waitingForClient;
  const awaitingReview = content.summary.awaitingClient;

  return (
    <div className="space-y-6">
      <header className="reveal flex flex-wrap items-baseline justify-between gap-2">
        <div>
          <h1 className="text-xl font-semibold tracking-[-0.02em] sm:text-2xl">
            Work &amp; Content
          </h1>
          <p className="text-sm text-muted-foreground">
            {work.editable
              ? 'What Vision is doing for the practice, and where each piece stands'
              : 'What Vision is doing, and what needs you'}
          </p>
        </div>
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
              <p className="text-sm font-semibold">
                {work.editable ? 'Waiting on the practice' : 'Waiting for you'}
              </p>
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

      <WorkPanel orgId={orgId} work={work} />
      <ContentPanel orgId={orgId} content={content} />

      <p className="reveal text-xs text-muted-foreground" style={{ '--i': 4 } as CSSProperties}>
        Approvals are recorded against the content item and written to the audit trail. Only items
        waiting on the practice can be decided, and only the practice can decide them, so an
        approval here always means the practice chose to give one.
      </p>
    </div>
  );
}


/* ------------------------------------------------------------------------------------------ work */

function WorkPanel({ orgId, work }: { orgId: string; work: WorkListResponse }) {
  const [filter, setFilter] = useUrlFilter('work', WORK_ORDER);
  const focusId = useRowFocus();
  const [creating, setCreating] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const { create, update } = useWorkAdmin(orgId);
  const transition = useWorkTransition(orgId);

  const items = filter ? work.items.filter((w) => w.status === filter) : work.items;
  const counts = countBy(work.items.map((w) => w.status));

  return (
    <Card className="reveal" style={{ '--i': 2 } as CSSProperties}>
      <CardHeader
        title="Work"
        action={
          work.editable && !creating ? (
            <button
              type="button"
              onClick={() => {
                create.reset();
                setCreating(true);
              }}
              className={cn(secondaryButton, 'inline-flex items-center gap-1.5')}
            >
              <Plus className="h-4 w-4" aria-hidden />
              New work item
            </button>
          ) : undefined
        }
      />
      <CardBody className="space-y-3">
        <StatusFilter
          label="Filter work by status"
          order={WORK_ORDER}
          counts={counts}
          tones={workTone}
          total={work.items.length}
          selected={filter}
          onSelect={setFilter}
        />

        {creating && (
          <WorkForm
            pending={create.isPending}
            error={create.error as ApiError | null}
            submitLabel="Create work item"
            onSubmit={(input) => create.mutate(input, { onSuccess: () => setCreating(false) })}
            onCancel={() => setCreating(false)}
          />
        )}

        {items.length === 0 && (
          <p className="py-6 text-center text-sm text-muted-foreground">
            {filter ? `No work is ${pretty(filter)}.` : 'No work items yet.'}
          </p>
        )}

        {items.map((w, i) =>
          editingId === w.id ? (
            <WorkForm
              key={w.id}
              initial={w}
              pending={update.isPending}
              error={update.error as ApiError | null}
              submitLabel="Save changes"
              onSubmit={(input) =>
                update.mutate({ id: w.id, ...input }, { onSuccess: () => setEditingId(null) })
              }
              onCancel={() => setEditingId(null)}
            />
          ) : (
            <article
              key={w.id}
                id={`row-${w.id}`}
              className={cn(
                  'reveal group rounded-lg border border-border/70 bg-card/60 p-4',
                  'transition-all duration-200 hover:border-primary/25 hover:elev-md',
                  focusId === w.id && 'ring-2 ring-primary ring-offset-2 ring-offset-background',
                )}
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

              {w.clientUpdate && (
                <p className="mt-3 flex items-start gap-2 text-sm">
                  <CircleDot className="mt-0.5 h-3.5 w-3.5 shrink-0 text-primary-text" aria-hidden />
                  <span>{w.clientUpdate}</span>
                </p>
              )}

              {w.clientDependency && (
                <p className="mt-2 flex items-start gap-2 rounded-md bg-caution/5 px-3 py-2 text-sm text-caution-text ring-1 ring-inset ring-caution/20">
                  <AlertTriangle className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden />
                  <span>
                    {work.editable
                      ? 'Waiting on the practice before Vision can continue.'
                      : 'Vision needs something from you before this can continue.'}
                  </span>
                </p>
              )}

              <div className="mt-3 flex flex-wrap items-center justify-between gap-3">
                <p className="flex items-center gap-1.5 text-xs text-muted-foreground">
                  <Clock className="h-3 w-3" aria-hidden />
                  {w.targetDate
                    ? `Target ${new Date(`${w.targetDate}T00:00:00`).toLocaleDateString(undefined, {
                        month: 'short',
                        day: 'numeric',
                      })}`
                    : 'No target date'}
                  <span aria-hidden>·</span>
                  {w.owner}
                </p>

                {work.editable && (
                  <div className="flex flex-wrap items-center gap-2">
                    <label className="flex items-center gap-1.5 text-xs text-muted-foreground">
                      Status
                      <select
                        value={w.status}
                        disabled={transition.isPending}
                        onChange={(e) =>
                          transition.mutate({ workItemId: w.id, toStatus: e.target.value as WorkStatus })
                        }
                        className="rounded-md border border-input bg-background px-2 py-1 text-xs text-foreground"
                      >
                        {WORK_ORDER.map((s) => (
                          <option key={s} value={s}>
                            {pretty(s)}
                          </option>
                        ))}
                      </select>
                    </label>
                    <button
                      type="button"
                      onClick={() => {
                        update.reset();
                        setEditingId(w.id);
                      }}
                      className={cn(secondaryButton, 'inline-flex items-center gap-1 px-2 py-1 text-xs')}
                    >
                      <Pencil className="h-3 w-3" aria-hidden />
                      Edit
                    </button>
                  </div>
                )}
              </div>

              {transition.error && transition.variables?.workItemId === w.id && (
                <p role="alert" className="mt-2 text-sm text-critical-text">
                  Not moved: {(transition.error as ApiError).message}
                </p>
              )}
            </article>
          ),
        )}
      </CardBody>
    </Card>
  );
}

/* --------------------------------------------------------------------------------------- content */

function ContentPanel({ orgId, content }: { orgId: string; content: ContentListResponse }) {
  const focusId = useRowFocus();
  const [filter, setFilter] = useUrlFilter('content', CONTENT_ORDER);
  const [creating, setCreating] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [feedbackFor, setFeedbackFor] = useState<string | null>(null);
  const [feedback, setFeedback] = useState('');

  const { approve, requestChanges } = useContentDecision(orgId);
  const { create, update, move } = useContentAdmin(orgId);

  const items = filter ? content.items.filter((c) => c.status === filter) : content.items;
  const counts = countBy(content.items.map((c) => c.status));
  const decisionError = (approve.error ?? requestChanges.error) as ApiError | null;

  return (
    <Card className="reveal" style={{ '--i': 3 } as CSSProperties}>
      <CardHeader
        title="Content"
        action={
          content.editable && !creating ? (
            <button
              type="button"
              onClick={() => {
                create.reset();
                setCreating(true);
              }}
              className={cn(secondaryButton, 'inline-flex items-center gap-1.5')}
            >
              <Plus className="h-4 w-4" aria-hidden />
              New content
            </button>
          ) : undefined
        }
      />
      <CardBody className="space-y-3">
        <StatusFilter
          label="Filter content by status"
          order={CONTENT_ORDER}
          counts={counts}
          tones={contentTone}
          total={content.items.length}
          selected={filter}
          onSelect={setFilter}
        />

        {creating && (
          <ContentForm
            pending={create.isPending}
            error={create.error as ApiError | null}
            submitLabel="Create content item"
            onSubmit={(input) => create.mutate(input, { onSuccess: () => setCreating(false) })}
            onCancel={() => setCreating(false)}
          />
        )}

        {items.length === 0 && (
          <p className="py-6 text-center text-sm text-muted-foreground">
            {filter ? `No content is ${pretty(filter)}.` : 'No content yet.'}
          </p>
        )}

        {items.map((c, i) => {
          const needsDecision = c.status === 'CLIENT_REVIEW';

          if (editingId === c.id) {
            return (
              <ContentForm
                key={c.id}
                initial={c}
                pending={update.isPending}
                error={update.error as ApiError | null}
                submitLabel="Save changes"
                onSubmit={(input) =>
                  update.mutate({ id: c.id, ...input }, { onSuccess: () => setEditingId(null) })
                }
                onCancel={() => setEditingId(null)}
              />
            );
          }

          return (
            <article
              key={c.id}
              id={`row-${c.id}`}
              className={cn(
                'reveal rounded-lg border p-4 transition-all duration-200',
                needsDecision
                  ? 'border-caution/30 bg-caution/5'
                  : 'border-border/70 bg-card/60 hover:border-primary/25 hover:elev-md',
                focusId === c.id && 'ring-2 ring-primary ring-offset-2 ring-offset-background',
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
                  {c.summary && <p className="mt-1.5 text-sm text-muted-foreground">{c.summary}</p>}
                </div>
                <span className="shrink-0 rounded-full bg-muted px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.06em] text-muted-foreground">
                  {pretty(c.contentType)}
                </span>
              </div>

              {c.clientFeedback && (
                <p className="mt-3 rounded-md bg-critical/5 px-3 py-2 text-xs italic text-critical-text">
                  {content.canDecide ? 'You asked for' : 'The practice asked for'}: {c.clientFeedback}
                </p>
              )}

              {/* Vision: move along its own stages, and edit. */}
              {content.editable && (
                <div className="mt-3 flex flex-wrap items-center gap-2 border-t border-border/60 pt-3">
                  {c.nextStatuses.map((next) => (
                    <button
                      key={next}
                      type="button"
                      disabled={move.isPending}
                      onClick={() => move.mutate({ id: c.id, toStatus: next })}
                      className={cn(
                        'rounded-md px-3 py-1.5 text-xs font-medium disabled:opacity-50',
                        next === 'CLIENT_REVIEW' || next === 'PUBLISHED'
                          ? 'bg-primary text-primary-foreground'
                          : 'border border-border bg-card transition-colors hover:bg-muted',
                      )}
                    >
                      {moveLabel[next]}
                    </button>
                  ))}
                  <button
                    type="button"
                    onClick={() => {
                      update.reset();
                      setEditingId(c.id);
                    }}
                    className={cn(secondaryButton, 'inline-flex items-center gap-1 px-2 py-1 text-xs')}
                  >
                    <Pencil className="h-3 w-3" aria-hidden />
                    Edit
                  </button>
                  {needsDecision && (
                    <span className="text-xs text-muted-foreground">
                      Waiting on the practice to approve or ask for changes.
                    </span>
                  )}
                  {c.nextStatuses.length === 0 && !needsDecision && (
                    <span className="text-xs text-muted-foreground">Published - nothing further.</span>
                  )}
                </div>
              )}
              {content.editable && move.error && move.variables?.id === c.id && (
                <p role="alert" className="mt-2 text-sm text-critical-text">
                  Not moved: {(move.error as ApiError).message}
                </p>
              )}

              {/* The practice: its two decisions, only while the item is waiting on it. */}
              {needsDecision && content.canDecide && (
                <div className="mt-3 space-y-2 border-t border-caution/20 pt-3">
                  {c.draftUrl && (
                    <a
                      href={c.draftUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="inline-block text-xs font-medium text-primary underline"
                    >
                      Read the draft first
                    </a>
                  )}

                  {feedbackFor === c.id ? (
                    <div className="space-y-2">
                      <textarea
                        value={feedback}
                        onChange={(event) => setFeedback(event.target.value)}
                        rows={3}
                        autoFocus
                        placeholder="What should change?"
                        aria-label="What should change"
                        className="w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                      />
                      <div className="flex flex-wrap gap-2">
                        <button
                          type="button"
                          disabled={!feedback.trim() || requestChanges.isPending}
                          onClick={() =>
                            requestChanges.mutate(
                              { contentItemId: c.id, feedback: feedback.trim() },
                              {
                                onSuccess: () => {
                                  setFeedbackFor(null);
                                  setFeedback('');
                                },
                              },
                            )
                          }
                          className="inline-flex min-h-11 items-center gap-1.5 rounded-full bg-primary px-4 text-sm font-semibold text-primary-foreground disabled:opacity-50"
                        >
                          Send
                        </button>
                        <button
                          type="button"
                          onClick={() => {
                            setFeedbackFor(null);
                            setFeedback('');
                          }}
                          className="inline-flex min-h-11 items-center rounded-full border border-border bg-card px-4 text-sm font-semibold"
                        >
                          Cancel
                        </button>
                      </div>
                    </div>
                  ) : (
                    <div className="flex flex-wrap gap-2">
                      <button
                        type="button"
                        disabled={approve.isPending}
                        onClick={() => approve.mutate(c.id)}
                        className="inline-flex min-h-11 items-center gap-1.5 rounded-full bg-primary px-4 text-sm font-semibold text-primary-foreground transition-all duration-150 hover:-translate-y-px hover:elev-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:opacity-50"
                      >
                        <Check className="h-4 w-4" aria-hidden />
                        {approve.isPending ? 'Approving…' : 'Approve'}
                      </button>
                      <button
                        type="button"
                        onClick={() => setFeedbackFor(c.id)}
                        className="inline-flex min-h-11 items-center gap-1.5 rounded-full border border-border bg-card px-4 text-sm font-semibold transition-colors hover:border-critical/40 hover:bg-critical/5 hover:text-critical-text focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                      >
                        <MessageSquareWarning className="h-4 w-4" aria-hidden />
                        Request changes
                      </button>
                    </div>
                  )}
                </div>
              )}
            </article>
          );
        })}

        {decisionError && (
          <p role="alert" className="text-sm text-critical-text">
            {decisionError.message}
          </p>
        )}
      </CardBody>
    </Card>
  );
}
