import type { ReactNode } from "react";
import { RouteMark } from "../components/Frame";

/** A drawn delivery round for the brand panel: a winding line with stops, the last one apricot. */
function RoundDrawing() {
  return (
    <svg viewBox="0 0 420 300" aria-hidden="true" className="w-full max-w-[460px]">
      <path
        d="M24 236 C 96 236, 92 150, 168 150 S 250 236, 318 196 S 380 74, 396 60"
        fill="none"
        stroke="#a9c2bb"
        strokeOpacity="0.55"
        strokeWidth="3"
        strokeLinecap="round"
        strokeDasharray="2 10"
      />
      <path d="M24 236 C 96 236, 92 150, 168 150 S 250 236, 318 196" fill="none" stroke="#f3ece0" strokeOpacity="0.85" strokeWidth="3" strokeLinecap="round" />
      {[
        [24, 236],
        [94.5, 193],
        [168, 150],
        [246, 188],
        [318, 196],
      ].map(([x, y]) => (
        <g key={`${x}-${y}`}>
          <circle cx={x} cy={y} r="11" fill="#173a36" />
          <circle cx={x} cy={y} r="7" fill="#f3ece0" />
        </g>
      ))}
      <circle cx="396" cy="60" r="15" fill="#173a36" />
      <circle cx="396" cy="60" r="10" fill="#f0b47a" />
      <rect x="318" y="84" width="40" height="32" rx="5" fill="none" stroke="#f0b47a" strokeWidth="2.5" />
      <path d="M318 94h40M338 84v10" stroke="#f0b47a" strokeWidth="2.5" />
    </svg>
  );
}

/** The pages without the frame: an evergreen brand panel beside the form (a band above it on phones). */
export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[minmax(360px,42%)_1fr]">
      <aside className="on-evergreen flex flex-col bg-evergreen px-5 py-5 text-cream sm:px-8 lg:min-h-dvh lg:px-12 lg:py-12">
        <div className="flex items-center gap-3">
          <RouteMark className="size-9 shrink-0" />
          <div>
            <p translate="no" className="text-[18px] font-bold leading-6">LMS</p>
            <p className="text-small text-menu-label">Logistics Management System</p>
          </div>
        </div>
        <div className="mt-auto hidden pt-12 lg:block">
          <RoundDrawing />
        </div>
      </aside>
      <main id="main" tabIndex={-1} className="flex justify-center px-4 py-8 sm:px-6 sm:py-14 lg:items-center lg:py-12">
        <div className="w-full max-w-[420px]">{children}</div>
      </main>
    </div>
  );
}
