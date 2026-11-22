import { useRef, useState, type FormEvent } from "react";
import { AuthLayout } from "./AuthLayout";
import { Button, MessagePlace, type MessageKind } from "../components/ui";
import { PasswordInput, TextField } from "../components/form";
import { login } from "../api/session";

export const LOGIN_NOTICES: Record<string, string> = {
  LOGGED_OUT: "You are logged out.",
  IDLE: "You were logged out because you were inactive.",
  SESSION_ENDED: "Your session has ended. Please log in again.",
  PASSWORD_CHANGED: "Password changed. Please log in with your new password.",
  PASSWORD_NOT_CHANGED: "Your session ended, so your new password was not saved. Please log in again.",
};

export function LoginPage() {
  const noticeKey = new URLSearchParams(window.location.search).get("sharedLoginUserNotice");
  const notice = noticeKey && Object.hasOwn(LOGIN_NOTICES, noticeKey) ? LOGIN_NOTICES[noticeKey] : null;
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<{ kind: MessageKind; text: string } | null>(notice ? { kind: "information", text: notice } : null);
  const userRef = useRef<HTMLInputElement>(null);
  const passwordRef = useRef<HTMLInputElement | null>(null);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (busy) return;
    setMessage(null);
    setBusy(true);
    const answer = await login(username, password);
    if (!answer.ok) {
      setBusy(false);
      setMessage({ kind: "error", text: answer.message });
      if (answer.field === "username" || !username.trim()) userRef.current?.focus();
      else passwordRef.current?.focus();
      return;
    }
    if (answer.data) {
      setPassword("");
      window.location.replace(answer.data.next);
    }
  };

  return (
    <AuthLayout>
      <form id="sharedLoginUserForm" onSubmit={submit} noValidate className="space-y-5">
        <div>
          <h1 className="text-page-title font-bold tracking-[-0.01em]">Log in</h1>
          <p className="mt-1 text-ink-muted">Use the username and password an Admin gave you.</p>
        </div>
        <MessagePlace id="sharedLoginUserMessage" message={message} />
        <TextField inputRef={userRef} label="Username" value={username} onChange={setUsername} autoComplete="username" autoCapitalize="none" spellCheck={false} id="sharedLoginUserUsernameInput" />
        <div className="flex flex-col gap-1">
          <label htmlFor="sharedLoginUserPasswordInput" className="text-label font-semibold">
            Password
          </label>
          <PasswordInput id="sharedLoginUserPasswordInput" name="password" label="Password" value={password} onChange={setPassword} autoComplete="current-password" inputRef={(el) => (passwordRef.current = el)} />
        </div>
        <Button id="sharedLoginUserSubmitButton" type="submit" level="main" busy={busy} busyText="Logging in…" className="w-full">
          Log in
        </Button>
      </form>
    </AuthLayout>
  );
}
