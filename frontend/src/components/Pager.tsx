import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "./ui";
import { cn } from "../lib/cn";

/** "Page 1 of 3", Previous, the page numbers and Next (20 rows a page). */
export function Pager({ page, pageCount, onPage, label }: { page: number; pageCount: number; onPage: (page: number) => void; label: string }) {
  const first = Math.max(1, Math.min(page - 2, pageCount - 4));
  const numbers = Array.from({ length: Math.min(5, pageCount) }, (_, i) => first + i);
  return (
    <nav aria-label={label} className="flex flex-wrap items-center justify-between gap-3 border-t border-line px-4 py-3 md:px-5">
      <p className="text-small tabular text-ink-muted">
        Page {page} of {pageCount}
      </p>
      <div className="flex items-center gap-1.5">
        <Button small disabled={page <= 1} onClick={() => onPage(page - 1)} icon={<ChevronLeft aria-hidden="true" className="size-4" />}>
          Previous
        </Button>
        <div className="hidden items-center gap-1 sm:flex">
          {numbers.map((n) => (
            <button
              key={n}
              type="button"
              onClick={() => onPage(n)}
              aria-current={n === page ? "page" : undefined}
              aria-label={`Page ${n}`}
              className={cn(
                "min-h-[34px] min-w-[34px] rounded-control px-2 text-label font-semibold tabular transition-colors duration-[120ms]",
                n === page ? "bg-teal-tint text-teal-hover" : "text-ink hover:bg-hover",
              )}
            >
              {n}
            </button>
          ))}
        </div>
        <Button small disabled={page >= pageCount} onClick={() => onPage(page + 1)} iconEnd={<ChevronRight aria-hidden="true" className="-mr-1 size-4" />}>
          Next
        </Button>
      </div>
    </nav>
  );
}
