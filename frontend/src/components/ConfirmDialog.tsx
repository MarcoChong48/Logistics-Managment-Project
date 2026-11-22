import { useId, useRef, useState, type ReactNode } from "react";
import { Dialog } from "./Dialog";
import { Button, MessagePlace, type ButtonLevel } from "./ui";
import type { ActionResult } from "../api/types";

/**
 * A window that asks for one decision. The confirm button stays switched off until canConfirm is true
 * (a reason of 5 characters, a needed tick). A refusal of the API is shown inside the window, which stays
 * open; a success calls onSuccess, which closes the window and shows the message.
 */
export function ConfirmDialog<T>({
  title,
  children,
  confirmLabel,
  busyLabel,
  level = "main",
  closeLabel = "Cancel",
  canConfirm = true,
  onConfirm,
  onSuccess,
  onClose,
  returnFocus,
  wide,
  requirement,
}: {
  title: ReactNode;
  children: ReactNode;
  confirmLabel: string;
  busyLabel?: string;
  level?: ButtonLevel;
  closeLabel?: "Cancel" | "Close";
  canConfirm?: boolean;
  onConfirm: () => Promise<ActionResult<T>>;
  onSuccess: (result: ActionResult<T>) => void;
  onClose: () => void;
  returnFocus?: () => HTMLElement | null;
  wide?: boolean;
  /** What the person still has to do while the confirm button is switched off, for example "Type a reason". */
  requirement?: string;
}) {
  const requirementID = useId();
  const pending = useRef(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const confirm = async () => {
    if (!canConfirm || pending.current) return;
    pending.current = true;
    setBusy(true);
    setError(null);
    try {
      const result = await onConfirm();
      if (result.ok) onSuccess(result);
      else setError(result.message);
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : "Could not complete the request.");
    } finally {
      pending.current = false;
      setBusy(false);
    }
  };
  const close = () => {
    if (!pending.current) onClose();
  };

  return (
    <Dialog
      title={title}
      onClose={close}
      returnFocus={returnFocus}
      wide={wide}
      footer={
        <>
          <Button onClick={close} disabled={busy}>{closeLabel}</Button>
          <Button
            level={level}
            disabled={!canConfirm}
            busy={busy}
            busyText={busyLabel ?? `${confirmLabel}…`}
            onClick={confirm}
            aria-describedby={!canConfirm && requirement ? requirementID : undefined}
          >
            {confirmLabel}
          </Button>
        </>
      }
    >
      {children}
      {!canConfirm && requirement && (
        <p id={requirementID} className="text-small text-ink-muted">
          {requirement}
        </p>
      )}
      <MessagePlace message={error ? { kind: "error", text: error } : null} />
    </Dialog>
  );
}

/** Runs after the window has closed, so the toast is not hidden behind it. */
export function afterClose(work: () => void): void {
  window.setTimeout(work, 40);
}
