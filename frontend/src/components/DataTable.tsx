// One list part for every table of the app. From 768 px it is a real table (a wide one scrolls inside its
// slip with the first column and the actions column pinned). Below 768 px every row becomes a card with the
// column titles above the values, so phones get the same rows and the same buttons. Work tables (the rows
// carry decision buttons) stay cards until 1280 px, so a decision button is never scrolled out of view.

import { useEffect, useRef, useState, type HTMLAttributes, type ReactNode } from "react";
import { cn } from "../lib/cn";
import { useWideTables, useWideWorkTables } from "../lib/useMediaQuery";

export interface Column<T> {
  key: string;
  title: string;
  cell: (row: T) => ReactNode;
  /** Numbers: right-aligned with tabular figures. */
  numeric?: boolean;
  className?: string;
  headClassName?: string;
  /** How the value shows on a phone card. The first column is the title unless a column says it is. */
  mobile?: "title" | "aside" | "field" | "wide" | "actions" | "hidden";
  /** Leave the value out of a phone card for this row (for example an actions cell with nothing to do). */
  mobileHidden?: (row: T) => boolean;
}

interface DataTableProps<T> {
  columns: Column<T>[];
  rows: T[];
  rowKey: (row: T) => string | number;
  /** The name of the table, for example "Users table". */
  label: string;
  empty?: ReactNode;
  rowProps?: (row: T) => HTMLAttributes<HTMLElement> & { [key: `data-${string}`]: string | undefined };
  /** Extra rows at the end of a real table, such as a total. */
  footer?: ReactNode;
  /** The same total for the phone cards. */
  mobileFooter?: ReactNode;
  minWidth?: string;
  /** "xl" for work tables: cards until 1280 px instead of 768 px. */
  cardsUntil?: "md" | "xl";
}

export function DataTable<T>({ columns, rows, rowKey, label, empty, rowProps, footer, mobileFooter, minWidth, cardsUntil = "md" }: DataTableProps<T>) {
  const tableFromMd = useWideTables();
  const tableFromXl = useWideWorkTables();
  const wide = cardsUntil === "xl" ? tableFromXl : tableFromMd;
  const scrollRef = useRef<HTMLDivElement>(null);
  const [overflowing, setOverflowing] = useState(false);

  useEffect(() => {
    const box = scrollRef.current;
    if (!box) return;
    const check = () => setOverflowing(box.scrollWidth > box.clientWidth + 1);
    check();
    const observer = new ResizeObserver(check);
    observer.observe(box);
    if (box.firstElementChild) observer.observe(box.firstElementChild);
    return () => observer.disconnect();
  }, [wide, rows.length]);

  if (rows.length === 0 && empty) return <>{empty}</>;

  if (!wide) {
    const declaresTitle = columns.some((c) => c.mobile === "title");
    const modeOf = (column: Column<T>, index: number) => column.mobile ?? (index === 0 && !declaresTitle ? "title" : "field");
    return (
      <div>
        <ul aria-label={label} className="divide-y divide-line">
          {rows.map((row) => {
            const extra = rowProps?.(row) ?? {};
            const title = columns.find((c, i) => modeOf(c, i) === "title");
            const aside = columns.find((c, i) => modeOf(c, i) === "aside");
            const fields = columns.filter((c, i) => ["field", "wide"].includes(modeOf(c, i)));
            const actions = columns.find((c, i) => modeOf(c, i) === "actions");
            return (
              <li key={rowKey(row)} {...extra} className={cn("px-4 py-4", extra.className)}>
                {(title || aside) && (
                  <div className="flex items-start justify-between gap-3">
                    {title && <div className="min-w-0 text-body font-semibold">{title.cell(row)}</div>}
                    {aside && <div className="shrink-0">{aside.cell(row)}</div>}
                  </div>
                )}
                {fields.length > 0 && (
                  <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-3 md:grid-cols-4 md:gap-x-6">
                    {fields.map((column) => (
                      <div key={column.key} className={cn("min-w-0", column.mobile === "wide" && "col-span-2")}>
                        <dt className="text-small text-ink-muted">{column.title}</dt>
                        <dd className={cn("mt-0.5 min-w-0 break-words", column.numeric && "tabular")}>{column.cell(row)}</dd>
                      </div>
                    ))}
                  </dl>
                )}
                {actions && !actions.mobileHidden?.(row) && <div className="mt-3.5">{actions.cell(row)}</div>}
              </li>
            );
          })}
        </ul>
        {mobileFooter}
      </div>
    );
  }

  const cellPad = "px-3 first:pl-4 last:pr-4 md:first:pl-5 md:last:pr-5";
  const pinEnd = overflowing && columns[columns.length - 1]?.mobile === "actions" ? columns.length - 1 : -1;
  return (
    <div ref={scrollRef} role="region" aria-label={label} tabIndex={overflowing ? 0 : undefined} className="lms-scroll-x">
      <table className={cn("w-full border-separate border-spacing-0 text-left", minWidth)}>
        <thead>
          <tr>
            {columns.map((column, index) => (
              <th
                key={column.key}
                scope="col"
                className={cn(
                  "whitespace-nowrap border-b border-line bg-sunken py-2.5 text-small font-semibold text-ink-muted",
                  cellPad,
                  column.numeric && "text-right",
                  overflowing && index === 0 && "sticky left-0 z-[1] shadow-[1px_0_0_var(--color-line),6px_0_8px_-6px_rgb(36_32_28/0.14)]",
                  index === pinEnd && "sticky right-0 z-[1] shadow-[-1px_0_0_var(--color-line),-6px_0_8px_-6px_rgb(36_32_28/0.14)]",
                  column.headClassName,
                )}
              >
                {column.title}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => {
            const extra = rowProps?.(row) ?? {};
            return (
              <tr key={rowKey(row)} {...extra} className={cn("group", extra.className)}>
                {columns.map((column, index) => (
                  <td
                    key={column.key}
                    className={cn(
                      "border-b border-line py-3 align-top transition-colors duration-[120ms] group-last:border-b-0 group-hover:bg-hover",
                      cellPad,
                      column.numeric && "whitespace-nowrap text-right tabular",
                      index === 0 && "font-semibold",
                      overflowing && index === 0 && "sticky left-0 z-[1] bg-surface shadow-[1px_0_0_var(--color-line),6px_0_8px_-6px_rgb(36_32_28/0.14)]",
                      index === pinEnd && "sticky right-0 z-[1] bg-surface shadow-[-1px_0_0_var(--color-line),-6px_0_8px_-6px_rgb(36_32_28/0.14)]",
                      column.className,
                    )}
                  >
                    {column.cell(row)}
                  </td>
                ))}
              </tr>
            );
          })}
        </tbody>
        {footer}
      </table>
    </div>
  );
}
