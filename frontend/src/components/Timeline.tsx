import { formatClock, isoAttribute } from "../lib/format";

/** The order timeline, grouped by day. Each entry shows its hour and minute; datetime holds the full time. */
export function Timeline({ days }: { days: Array<{ day: string; entries: Array<{ time: number; text: string; actor: string | null }> }> }) {
  return (
    <div className="space-y-6">
      {days.map((block) => (
        <section key={block.day} aria-label={block.day}>
          <h3 className="mb-3 inline-flex rounded-chip bg-sunken px-2 py-0.5 text-small font-semibold tabular text-ink-muted">{block.day}</h3>
          <ol className="relative ml-[5px] space-y-3 border-l border-line-strong pl-5">
            {block.entries.map((entry, index) => (
              <li key={`${entry.time}-${index}`} className="relative">
                <span aria-hidden="true" className="absolute -left-[25px] top-[7px] size-2 rounded-full border-2 border-surface bg-line-strong outline outline-1 outline-line-strong" />
                <div className="flex gap-3">
                  <time dateTime={isoAttribute(entry.time)} className="w-11 shrink-0 pt-px text-small font-semibold tabular text-ink-muted">
                    {formatClock(entry.time)}
                  </time>
                  <div className="min-w-0">
                    <p className="break-words">{entry.text}</p>
                    {entry.actor && <p className="text-small text-ink-muted">by {entry.actor}</p>}
                  </div>
                </div>
              </li>
            ))}
          </ol>
        </section>
      ))}
    </div>
  );
}
