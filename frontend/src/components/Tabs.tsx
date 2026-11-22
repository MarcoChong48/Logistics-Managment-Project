// Tabs (Logs): only the selected tab is a Tab stop; arrow keys, Home and End move between tabs.
// The teal bar under the tabs slides to the chosen tab.

import { useLayoutEffect, useRef, useState } from "react";
import { cn } from "../lib/cn";

export interface TabItem {
  id: string;
  label: string;
  note?: string;
}

export function Tabs({ label, tabs, active, onChange }: { label: string; tabs: TabItem[]; active: string; onChange: (id: string) => void }) {
  const listRef = useRef<HTMLDivElement>(null);
  const [bar, setBar] = useState({ left: 0, width: 0 });

  useLayoutEffect(() => {
    const measure = () => {
      const tab = listRef.current?.querySelector<HTMLElement>(`[data-tab="${active}"]`);
      if (tab) setBar({ left: tab.offsetLeft, width: tab.offsetWidth });
    };
    measure();
    window.addEventListener("resize", measure);
    return () => window.removeEventListener("resize", measure);
  }, [active]);

  const move = (index: number) => {
    const next = tabs[(index + tabs.length) % tabs.length];
    onChange(next.id);
    listRef.current?.querySelector<HTMLElement>(`[data-tab="${next.id}"]`)?.focus();
  };

  return (
    <div className="lms-scroll-x border-b border-line">
      <div ref={listRef} role="tablist" aria-label={label} className="relative flex min-w-max gap-1">
        {tabs.map((tab, index) => {
          const selected = tab.id === active;
          return (
            <button
              key={tab.id}
              id={`${tab.id}-tab`}
              data-tab={tab.id}
              type="button"
              role="tab"
              aria-selected={selected}
              aria-controls={`${tab.id}-panel`}
              tabIndex={selected ? 0 : -1}
              onClick={() => onChange(tab.id)}
              onKeyDown={(event) => {
                if (event.key === "ArrowRight") {
                  event.preventDefault();
                  move(index + 1);
                } else if (event.key === "ArrowLeft") {
                  event.preventDefault();
                  move(index - 1);
                } else if (event.key === "Home") {
                  event.preventDefault();
                  move(0);
                } else if (event.key === "End") {
                  event.preventDefault();
                  move(tabs.length - 1);
                }
              }}
              className={cn(
                "flex min-h-11 items-baseline gap-2 rounded-t-control px-3 pb-3 pt-2.5 text-body font-semibold transition-colors duration-[120ms]",
                selected ? "text-teal-hover" : "text-ink-muted hover:text-ink",
              )}
            >
              {tab.label}
              {tab.note && <span className="hidden text-small font-normal text-ink-muted sm:inline">{tab.note}</span>}
            </button>
          );
        })}
        <span
          aria-hidden="true"
          className="absolute bottom-0 left-0 h-[3px] w-[100px] origin-left rounded-full bg-teal transition-transform duration-[160ms] ease-out"
          style={{ transform: `translateX(${bar.left}px) scaleX(${bar.width / 100})` }}
        />
      </div>
    </div>
  );
}
