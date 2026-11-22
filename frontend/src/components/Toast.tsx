// Toasts: the message after a decision. Top right on desktop, under the top bar on phones. They leave
// after 5 seconds, sit in a polite live region, and are cleared when a window opens.

import { createContext, useCallback, useContext, useMemo, useRef, useState, type ReactNode } from "react";
import { CheckCircle2, AlertTriangle, Info } from "lucide-react";
import { cn } from "../lib/cn";
import type { MessageKind } from "./ui";

interface ToastItem {
  id: number;
  kind: MessageKind;
  text: string;
}

interface ToastAPI {
  show: (kind: MessageKind, text: string) => void;
  clear: () => void;
}

const ToastContext = createContext<ToastAPI>({ show: () => undefined, clear: () => undefined });

export function useToast(): ToastAPI {
  return useContext(ToastContext);
}

const ICON: Record<MessageKind, ReactNode> = {
  success: <CheckCircle2 aria-hidden="true" className="mt-px size-5 shrink-0 text-done-fg" />,
  error: <AlertTriangle aria-hidden="true" className="mt-px size-5 shrink-0 text-danger" />,
  information: <Info aria-hidden="true" className="mt-px size-5 shrink-0 text-teal" />,
};

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);
  const timers = useRef(new Map<number, number>());
  const counter = useRef(0);

  const clear = useCallback(() => {
    timers.current.forEach((timer) => window.clearTimeout(timer));
    timers.current.clear();
    setItems([]);
  }, []);

  const leaveLater = useCallback((id: number, delay: number) => {
    window.clearTimeout(timers.current.get(id));
    const timer = window.setTimeout(() => {
      setItems((list) => list.filter((item) => item.id !== id));
      timers.current.delete(id);
    }, delay);
    timers.current.set(id, timer);
  }, []);

  const show = useCallback(
    (kind: MessageKind, text: string) => {
      if (document.querySelector("dialog[open]:modal")) return;
      counter.current += 1;
      const id = counter.current;
      setItems((list) => [...list.slice(-2), { id, kind, text }]);
      leaveLater(id, 6000);
    },
    [leaveLater],
  );

  const api = useMemo(() => ({ show, clear }), [show, clear]);

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div aria-live="polite" className="pointer-events-none fixed inset-x-2 top-[64px] z-[60] flex flex-col items-stretch gap-2 sm:inset-x-auto sm:right-6 sm:top-[72px] sm:w-[400px]">
        {items.map((item) => (
          <div
            key={item.id}
            role="status"
            onMouseEnter={() => window.clearTimeout(timers.current.get(item.id))}
            onMouseLeave={() => leaveLater(item.id, 3000)}
            className={cn("lms-toast-in pointer-events-auto flex items-start gap-3 rounded-card border border-line bg-surface px-4 py-3 text-body font-medium shadow-raised")}
          >
            {ICON[item.kind]}
            <p className="min-w-0">{item.text}</p>
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}
