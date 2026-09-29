import { useState } from 'react';
import type { ApiError } from '@/lib/api';
import type { ContentItemRow, ContentType } from '@/lib/types';
import type { ContentInput } from './useWorkContent';
import { fieldInput, fieldLabel, primaryButton, secondaryButton } from './formStyles';

const TYPES: ContentType[] = [
  'BLOG',
  'SOCIAL_POST',
  'SHORT_VIDEO',
  'GOOGLE_BUSINESS_POST',
  'LANDING_PAGE',
  'FAQ',
];

const pretty = (s: string) => s.charAt(0) + s.slice(1).toLowerCase().replace(/_/g, ' ');

/**
 * Create or edit a content item. Vision only.
 *
 * Editing never changes status - that is what the move buttons are for, and they obey the approval
 * rules. The summary is what the practice reads when deciding, so it is worth writing well.
 */
export function ContentForm({
  initial,
  pending,
  error,
  submitLabel,
  onSubmit,
  onCancel,
}: {
  initial?: ContentItemRow;
  pending: boolean;
  error: ApiError | null;
  submitLabel: string;
  onSubmit: (input: ContentInput) => void;
  onCancel: () => void;
}) {
  const [title, setTitle] = useState(initial?.title ?? '');
  const [summary, setSummary] = useState(initial?.summary ?? '');
  const [contentType, setContentType] = useState<ContentType>(initial?.contentType ?? 'BLOG');
  const [author, setAuthor] = useState(initial?.author ?? 'Vision Digital Lab');
  const [draftUrl, setDraftUrl] = useState(initial?.draftUrl ?? '');
  const [publishedUrl, setPublishedUrl] = useState(initial?.publishedUrl ?? '');

  const ready = title.trim() !== '' && author.trim() !== '';

  return (
    <form
      className="space-y-4 rounded-lg border border-primary/30 bg-primary-soft/30 p-4"
      onSubmit={(event) => {
        event.preventDefault();
        onSubmit({
          title: title.trim(),
          summary: summary.trim() || null,
          contentType,
          author: author.trim(),
          draftUrl: draftUrl.trim() || null,
          publishedUrl: publishedUrl.trim() || null,
        });
      }}
    >
      <div className="grid gap-4 sm:grid-cols-[1fr_220px]">
        <label className="block">
          <span className={fieldLabel}>Title</span>
          <input value={title} maxLength={240} onChange={(e) => setTitle(e.target.value)} className={fieldInput} />
        </label>
        <label className="block">
          <span className={fieldLabel}>Type</span>
          <select value={contentType} onChange={(e) => setContentType(e.target.value as ContentType)} className={fieldInput}>
            {TYPES.map((t) => (
              <option key={t} value={t}>
                {pretty(t)}
              </option>
            ))}
          </select>
        </label>
      </div>
      <label className="block">
        <span className={fieldLabel}>One-line summary for the practice</span>
        <input
          value={summary}
          maxLength={500}
          onChange={(e) => setSummary(e.target.value)}
          placeholder="What they are approving, without opening the draft."
          className={fieldInput}
        />
      </label>
      <div className="grid gap-4 sm:grid-cols-3">
        <label className="block">
          <span className={fieldLabel}>Author</span>
          <input value={author} maxLength={160} onChange={(e) => setAuthor(e.target.value)} className={fieldInput} />
        </label>
        <label className="block">
          <span className={fieldLabel}>Draft link</span>
          <input type="url" value={draftUrl} maxLength={500} onChange={(e) => setDraftUrl(e.target.value)} placeholder="https://" className={fieldInput} />
        </label>
        <label className="block">
          <span className={fieldLabel}>Published link</span>
          <input type="url" value={publishedUrl} maxLength={500} onChange={(e) => setPublishedUrl(e.target.value)} placeholder="https://" className={fieldInput} />
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
