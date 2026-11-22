// A window: the native <dialog> opened with showModal(), so the keyboard stays inside it, Escape closes it
// and the page behind cannot be used. A click on the dark layer does nothing. When the window closes,
// the keyboard goes back to the control that opened it (or to the element named by returnFocus).

import { useEffect, useId, useRef, type ReactNode } from "react";
import { cn } from "../lib/cn";
import { useToast } from "./Toast";

export interface DialogProps {
  title: ReactNode;
  onClose: () => void;
  children: ReactNode;
  footer: ReactNode;
  wide?: boolean;
  /** Where the keyboard goes when the window closes; by default the control that opened it. */
  returnFocus?: () => HTMLElement | null;
  /** Elements outside the window may stay visible behind it (the print label). */
  className?: string;
}

export function Dialog({ title, onClose, children, footer, wide = false, returnFocus, className }: DialogProps) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleID = useId();
  const toast = useToast();
  const returnRef = useRef(returnFocus);
  returnRef.current = returnFocus;

  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    const opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    toast.clear();
    if (!dialog.open) dialog.showModal();
    return () => {
      if (dialog.open) dialog.close();
      const target = returnRef.current?.() ?? opener;
      window.requestAnimationFrame(() => {
        if (target && target.isConnected) target.focus();
      });
    };
    // The window opens once per mount; a new opening mounts a new window with fresh fields.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <dialog
      ref={ref}
      aria-labelledby={titleID}
      className={cn("lms-dialog m-auto", className)}
      onCancel={(event) => {
        event.preventDefault();
        onClose();
      }}
    >
      <div
        className={cn(
          "flex max-h-[calc(100dvh-24px)] w-[calc(100vw-16px)] flex-col rounded-window bg-surface text-ink shadow-window sm:max-h-[calc(100dvh-48px)]",
          wide ? "sm:w-[640px]" : "sm:w-[520px]",
        )}
      >
        <div className="px-5 pb-2 pt-5">
          <h2 id={titleID} className="text-card-title font-[650]">
            {title}
          </h2>
        </div>
        <div className="flex min-h-0 flex-col gap-4 overflow-y-auto overscroll-contain px-5 pb-5 pt-2">{children}</div>
        <div className="flex flex-col-reverse gap-2 border-t border-line px-5 py-4 sm:flex-row sm:justify-end [&>*]:max-sm:w-full">{footer}</div>
      </div>
    </dialog>
  );
}
