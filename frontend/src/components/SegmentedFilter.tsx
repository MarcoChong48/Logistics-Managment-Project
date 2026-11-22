import { cn } from "../lib/cn";

export interface SegmentOption {
  value: string;
  label: string;
  count?: number;
}

/**
 * A row of filter buttons that show how many rows each filter holds ("Needs decision 3").
 * The chosen button is teal ("you are here"); a filter with nothing in it is quieter but still works.
 */
export function SegmentedFilter({ label, options, value, onChange }: { label: string; options: SegmentOption[]; value: string; onChange: (value: string) => void }) {
  return (
    <div role="group" aria-label={label} className="flex flex-wrap gap-1.5">
      {options.map((option) => {
        const pressed = option.value === value;
        return (
          <button
            key={option.value}
            type="button"
            aria-pressed={pressed}
            onClick={() => onChange(option.value)}
            className={cn(
              "inline-flex min-h-9 items-center gap-2 rounded-full border px-3 text-label font-semibold transition-colors duration-[120ms] pointer-coarse:min-h-10",
              pressed ? "border-teal bg-teal-tint text-teal-hover" : "border-line bg-surface text-ink hover:border-field-line hover:bg-hover",
              !pressed && option.count === 0 && "text-ink-muted",
            )}
          >
            {option.label}
            {option.count !== undefined && <span className={cn("tabular text-small", pressed ? "text-teal-hover" : "text-ink-muted")}>{option.count}</span>}
          </button>
        );
      })}
    </div>
  );
}
