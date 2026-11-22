import { Button, MessagePlace } from "./ui";

export function WorkQueryNotice({ pending, error, refresh }: { pending: boolean; error?: string; refresh: () => void }) {
  if (error) return (
    <div className="space-y-2 px-4 py-3">
      <MessagePlace message={{ kind: "error", text: error }} />
      <Button onClick={refresh} small>Try again</Button>
    </div>
  );
  if (pending) return <p role="status" className="px-4 py-3 text-ink-muted">Loading…</p>;
  return null;
}
