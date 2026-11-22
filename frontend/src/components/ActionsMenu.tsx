// One "Actions" menu per row of the long lists. Normal lines first, then a divider and the dangerous
// lines in red. Escape, a click elsewhere, scrolling the page or resizing the window close it. The menu
// opens below its button, or above it when there is no room, and never leaves the window.

import { useCallback, useEffect, useId, useLayoutEffect, useRef, useState, type KeyboardEvent as ReactKeyboardEvent } from "react";
import { createPortal } from "react-dom";
import { ChevronDown } from "lucide-react";
import { buttonClass } from "./ui";
import { cn } from "../lib/cn";

export interface MenuLine {
  label: string;
  onSelect: () => void;
}

export function ActionsMenu({ label, lines, dangerLines = [] }: { label: string; lines: MenuLine[]; dangerLines?: MenuLine[] }) {
  const [open, setOpen] = useState(false);
  const [position, setPosition] = useState<{ top: number; left: number; maxHeight: number } | null>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const menuRef = useRef<HTMLDivElement>(null);
  const openedByKeyboard = useRef(false);
  const menuID = useId();

  const close = useCallback((focusButton: boolean) => {
    setOpen(false);
    setPosition(null);
    if (focusButton) buttonRef.current?.focus();
  }, []);

  const place = useCallback(() => {
    if (!buttonRef.current || !menuRef.current) return;
    const rect = buttonRef.current.getBoundingClientRect();
    if (rect.bottom < 0 || rect.top > window.innerHeight) {
      close(false);
      return;
    }
    const menu = menuRef.current.getBoundingClientRect();
    const maxHeight = window.innerHeight - 16;
    const height = Math.min(menu.height, maxHeight);
    let top = rect.bottom + 4;
    if (top + height > window.innerHeight - 8) top = rect.top - height - 4;
    if (top < 8) top = 8;
    const left = Math.min(Math.max(8, rect.right - menu.width), window.innerWidth - menu.width - 8);
    setPosition({ top, left, maxHeight });
  }, [close]);

  useLayoutEffect(() => {
    if (open) place();
  }, [open, place]);

  useEffect(() => {
    if (!open) return;
    if (openedByKeyboard.current) menuRef.current?.querySelector<HTMLButtonElement>("button")?.focus();
    const onPointer = (event: PointerEvent) => {
      const target = event.target as Node;
      if (menuRef.current?.contains(target) || buttonRef.current?.contains(target)) return;
      close(false);
    };
    const onScroll = (event: Event) => {
      if (menuRef.current?.contains(event.target as Node)) return;
      place();
    };
    const onResize = () => place();
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        close(true);
      }
    };
    document.addEventListener("keydown", onKey);
    document.addEventListener("pointerdown", onPointer);
    window.addEventListener("scroll", onScroll, true);
    window.addEventListener("resize", onResize);
    return () => {
      document.removeEventListener("keydown", onKey);
      document.removeEventListener("pointerdown", onPointer);
      window.removeEventListener("scroll", onScroll, true);
      window.removeEventListener("resize", onResize);
    };
  }, [open, close, place]);

  const choose = (line: MenuLine) => {
    close(true);
    line.onSelect();
  };

  const onMenuKey = (event: ReactKeyboardEvent) => {
    const items = Array.from(menuRef.current?.querySelectorAll<HTMLButtonElement>("button") ?? []);
    const index = items.indexOf(document.activeElement as HTMLButtonElement);
    if (event.key === "ArrowDown") {
      event.preventDefault();
      items[(index + 1) % items.length]?.focus();
    } else if (event.key === "ArrowUp") {
      event.preventDefault();
      items[(index - 1 + items.length) % items.length]?.focus();
    } else if (event.key === "Home") {
      event.preventDefault();
      items[0]?.focus();
    } else if (event.key === "End") {
      event.preventDefault();
      items.at(-1)?.focus();
    }
  };

  const lineClass = "flex w-full min-h-10 items-center rounded-control px-3 text-left text-body font-medium transition-colors duration-[120ms] pointer-coarse:min-h-11";

  return (
    <>
      <button
        ref={buttonRef}
        type="button"
        aria-label={label}
        aria-expanded={open}
        aria-controls={open ? menuID : undefined}
        onClick={() => {
          openedByKeyboard.current = false;
          setOpen((o) => !o);
        }}
        onKeyDown={(event) => {
          if (event.key === "Enter" || event.key === " " || event.key === "ArrowDown") {
            event.preventDefault();
            openedByKeyboard.current = true;
            setOpen(true);
          }
        }}
        className={buttonClass("secondary", true)}
      >
        Actions
        <ChevronDown aria-hidden="true" className={cn("size-4 transition-transform duration-[140ms]", open && "rotate-180")} />
      </button>
      {open &&
        createPortal(
          <div
            ref={menuRef}
            id={menuID}
            onKeyDown={onMenuKey}
            onBlur={(event) => {
              const next = event.relatedTarget as Node | null;
              if (next && !menuRef.current?.contains(next) && !buttonRef.current?.contains(next)) close(false);
            }}
            style={{ top: position?.top ?? -9999, left: position?.left ?? -9999, maxHeight: position?.maxHeight }}
            className="lms-menu-in fixed z-[70] min-w-[200px] overflow-y-auto overscroll-contain rounded-card border border-line bg-surface p-1.5 shadow-raised"
          >
            {lines.map((line) => (
              <button key={line.label} type="button" onClick={() => choose(line)} className={cn(lineClass, "text-ink hover:bg-hover")}>
                {line.label}
              </button>
            ))}
            {dangerLines.length > 0 && lines.length > 0 && <div role="separator" className="mx-2 my-1.5 h-px bg-line" />}
            {dangerLines.map((line) => (
              <button key={line.label} type="button" onClick={() => choose(line)} className={cn(lineClass, "text-danger hover:bg-danger-tint")}>
                {line.label}
              </button>
            ))}
          </div>,
          document.body,
        )}
    </>
  );
}
