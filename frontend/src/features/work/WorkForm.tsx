import { useState } from 'react';
import type { ApiError } from '@/lib/api';
import type { WorkCategory, WorkItemRow } from '@/lib/types';
import type { WorkInput } from './useWorkContent';
import { fieldInput, fieldLabel, primaryButton, secondaryButton } from './formStyles';

const CATEGORIES: WorkCategory[] = [
  'SEO',
  'PAID_ACQUISITION',
  'LOCAL_SEARCH',
  'CONTENT',
  'SOCIAL',
  'REPUTATION',
  'ANALYTICS',
  'INTEGRATION',
  'AUTOMATION',
];

const pretty = (s: string) => s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, ' ');

/**
 * Create or edit a work item. Vision only.
 *
 * The business reason is required and sits second, straight after the title: the practice reads
 * "why" before "what", so a work item without one is not ready to be shown to them.
 */
export function WorkForm({
  initial,
  pending,
  error,
  submitLabel,
  onSubmit,
  onCancel,
}: {
  initial?: WorkItemRow;
  pending: boolean;
  error: ApiError | null;
  submitLabel: string;
  onSubmit: (input: WorkInput) => void;
  onCancel: () => void;
}) {
  const [title, setTitle] = useState(initial?.title ?? '');
  const [category, setCategory] = useState<WorkCategory>(initial?.category ?? 'SEO');
  const [businessReason, setBusinessReason] = useState(initial?.businessReason ?? '');
  const [owner, setOwner] = useState(initial?.owner ?? 'Vision Digital Lab');
  const [targetDate, setTargetDate] = useState(initial?.targetDate ?? '');
  const [clientUpdate, setClientUpdate] = useState(initial?.clientUpdate ?? '');

  const ready = title.trim() !== '' && businessReason.trim() !== '' && owner.trim() !== '';

  return (
    <form
      className="space-y-4 rounded-lg border border-primary/30 bg-primary-soft/30 p-4"
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit({
          title: title.trim(),
          category,
          businessReason: businessReason.trim(),
          owner: owner.trim(),
          targetDate: targetDate || null,
          clientUpdate: clientUpdate.trim() || null,
        });
      }}
    >
      <div className="grid gap-4 sm:grid-cols-[1fr_200px]">
        <label className="block">
          <span className={fieldLabel}>Title</span>
          <input value={title} maxLength={240} onChange={(e) => setTitle(e.target.value)} className={fieldInput} />
        </label>
        <label className="block">
          <span className={fieldLabel}>Category</span>
          <select value={category} onChange={(e) => setCategory(e.target.value as WorkCategory)} className={fieldInput}>
            {CATEGORIES.map((c) => (
              <option key={c} value={c}>
                {pretty(c)}
              </option>
            ))}
          </select>
        </label>
      </div>
      <label className="block">
        <span className={fieldLabel}>Why it matters to the practice</span>
        <textarea
          rows={2}
          maxLength={4000}
          value={businessReason}
          onChange={(e) => setBusinessReason(e.target.value)}
          placeholder="The business reason, in the practice's terms."
          className={fieldInput}
        />
      </label>
      <label className="block">
        <span className={fieldLabel}>Latest update for the practice</span>
        <textarea
          rows={2}
          maxLength={4000}
          value={clientUpdate}
          onChange={(e) => setClientUpdate(e.target.value)}
          className={fieldInput}
        />
      </label>
      <div className="grid gap-4 sm:grid-cols-2">
        <label className="block">
          <span className={fieldLabel}>Owner</span>
          <input value={owner} maxLength={160} onChange={(e) => setOwner(e.target.value)} className={fieldInput} />
        </label>
        <label className="block">
          <span className={fieldLabel}>Target date</span>
          <input type="date" value={targetDate} onChange={(e) => setTargetDate(e.target.value)} className={fieldInput} />
        </label>
      </div>
      <div className="flex flex-wrap items-center gap-2">
        <button type="submit" disabled={pending || !ready} className={primaryButton}>
          {pending ? 'Saving…' : submitLabel}
        </button>
        <button type="button" onClick={onCancel} className={secondaryButton}>
          Cancel
        </button>
        {error && (
          <span role="alert" className="text-sm text-critical-text">
            Not saved: {error.message}
          </span>
        )}
      </div>
    </form>
  );
}
