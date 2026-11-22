import { ChevronRight } from "lucide-react";

/** "How to use": a worded disclosure with the lines of a test tool (Creates, Before, See it on ...). */
export function HowToUse({ lines }: { lines: Array<[string, string]> }) {
  if (lines.length === 0) return null;
  return (
    <details className="group rounded-control border border-line bg-sunken/70">
      <summary className="flex min-h-10 cursor-pointer list-none items-center gap-1.5 rounded-control px-3 text-label font-semibold text-teal-hover hover:bg-sunken pointer-coarse:min-h-11 [&::-webkit-details-marker]:hidden">
        <ChevronRight aria-hidden="true" className="size-4 transition-transform duration-[140ms] group-open:rotate-90" />
        How to use
      </summary>
      <dl className="space-y-2 px-3 pb-3 pt-1">
        {lines.map(([term, text]) => (
          <div key={term} className="grid gap-0.5 sm:grid-cols-[84px_minmax(0,1fr)] sm:gap-3">
            <dt className="text-small font-semibold text-ink-muted">{term}</dt>
            <dd className="text-small text-ink">{text}</dd>
          </div>
        ))}
      </dl>
    </details>
  );
}
