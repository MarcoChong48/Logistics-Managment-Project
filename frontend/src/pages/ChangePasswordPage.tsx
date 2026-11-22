// The shared Change password page. One page for both cases: a person who chose "Change password", and a
// person who logged in with a temporary password from an Admin ("Choose your own password").
// Live rule list, an advisory strength scale (amber or green, never red), Show / Hide, errors under fields.

import { useEffect, useRef, useState, type FormEvent } from "react";
import { Check, Circle, X } from "lucide-react";
import { AuthLayout } from "./AuthLayout";
import { Button, buttonClass, InlineMessage, MessagePlace, type MessageKind } from "../components/ui";
import { PasswordInput } from "../components/form";
import { changePassword, logout, type LiveSession } from "../api/session";
import { cn } from "../lib/cn";

type RuleState = "pending" | "met" | "broken";
type Level = "none" | "weak" | "fair" | "good" | "strong";

const bytesOf = (text: string) => new TextEncoder().encode(text).length;
const charactersOf = (text: string) => Array.from(text).length;

function rules(password: string) {
  const length = charactersOf(password);
  return {
    length: length >= 8 && length <= 72 && bytesOf(password) <= 72,
    capital: /[A-Z]/.test(password),
    small: /[a-z]/.test(password),
    digit: /[0-9]/.test(password),
  };
}

function strength(password: string): Level {
  if (!password) return "none";
  const r = rules(password);
  if (!r.length || !r.capital || !r.small || !r.digit) return "weak";
  const long = charactersOf(password) >= 12;
  const symbol = /[^A-Za-z0-9]/.test(password);
  if (long && symbol) return "strong";
  if (long || symbol) return "good";
  return "fair";
}

const LEVEL_WORD: Record<Level, string> = { none: "not rated yet", weak: "too weak", fair: "fair", good: "good", strong: "strong" };
const LEVEL_STEPS: Record<Level, number> = { none: 0, weak: 1, fair: 2, good: 3, strong: 4 };

function RuleLine({ state, children }: { state: RuleState; children: string }) {
  const hidden = state === "met" ? "Done: " : state === "broken" ? "Not met: " : "Still needed: ";
  return (
    <li className={cn("flex items-center gap-2 text-small", state === "met" && "text-done-fg", state === "broken" && "font-semibold text-danger", state === "pending" && "text-ink-muted")}>
      {state === "met" && (
        <span aria-hidden="true" className="grid size-4 place-items-center rounded-full bg-done-fg text-white">
          <Check className="size-3" strokeWidth={3} />
        </span>
      )}
      {state === "broken" && (
        <span aria-hidden="true" className="grid size-4 place-items-center rounded-full bg-danger text-white">
          <X className="size-3" strokeWidth={3} />
        </span>
      )}
      {state === "pending" && <Circle aria-hidden="true" className="size-4 text-field-line" />}
      <span>
        <span className="sr-only">{hidden}</span>
        {children}
      </span>
    </li>
  );
}

function FieldError({ id, text }: { id: string; text: string | null }) {
  return (
    <p id={id} className={cn("text-small font-semibold text-danger", !text && "hidden")}>
      {text}
    </p>
  );
}

export function ChangePasswordPage({ session }: { session: LiveSession }) {
  const forced = session.mustChangePassword;
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [repeat, setRepeat] = useState("");
  const [pressed, setPressed] = useState(false);
  const [busy, setBusy] = useState(false);
  const [loggingOut, setLoggingOut] = useState(false);
  const [errors, setErrors] = useState<{ current: string | null; next: string | null; repeat: string | null }>({ current: null, next: null, repeat: null });
  const [message, setMessage] = useState<{ kind: MessageKind; text: string } | null>(null);
  const inputs = useRef<Record<string, HTMLInputElement | null>>({});

  useEffect(() => {
    document.title = forced ? "Choose your own password - LMS" : "Change password - LMS";
  }, [forced]);

  const r = rules(next);
  const characters = charactersOf(next);
  const tooManyBytes = characters <= 72 && bytesOf(next) > 72;
  const ruleState = (met: boolean, alwaysRed = false): RuleState => (met ? "met" : pressed || alwaysRed ? "broken" : "pending");
  const level = strength(next);
  const sameAsCurrent = Boolean(current && next && current === next);
  const allRules = r.length && r.capital && r.small && r.digit;
  const matchState: RuleState = repeat && repeat === next ? "met" : pressed ? "broken" : "pending";

  const liveNextError = tooManyBytes
    ? "Too long: some of these characters count as more than one. Use fewer characters."
    : characters > 72
      ? "Use at most 72 characters."
      : sameAsCurrent
        ? "The new password must be different from the current one."
        : pressed && !allRules
          ? "Meet every rule marked in red."
          : null;

  const shown = {
    current: errors.current,
    next: errors.next ?? liveNextError,
    repeat: errors.repeat,
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (busy || loggingOut) return;
    setPressed(true);
    setMessage(null);
    const found = {
      current: current ? null : forced ? "Type your temporary password." : "Type your current password.",
      next: next ? null : "Type a new password.",
      repeat: repeat ? null : "Type the new password again.",
    };
    if (found.current || found.next || found.repeat) {
      setErrors(found);
      const first = found.current ? "current" : found.next ? "next" : "repeat";
      inputs.current[first]?.focus();
      return;
    }
    setErrors({ current: null, next: null, repeat: null });
    setBusy(true);
    const answer = await changePassword(current, next, repeat);
    if (answer.ok || answer.field === "session") return;
    setBusy(false);
    const field = answer.field === "current" ? "current" : answer.field === "confirm" ? "repeat" : answer.field === "new" ? "next" : null;
    if (field) {
      setErrors((e) => ({ ...e, [field]: answer.message }));
      inputs.current[field]?.focus();
    } else {
      setMessage({ kind: "error", text: answer.message });
    }
  };

  const doLogout = async () => {
    setLoggingOut(true);
    setMessage(null);
    const answer = await logout();
    if (!answer.ok) {
      setLoggingOut(false);
      setMessage({ kind: "error", text: answer.message });
    }
  };

  const invalid = (key: "current" | "next" | "repeat") => Boolean(shown[key]) || (pressed && key === "next" && !allRules) || (pressed && key === "repeat" && matchState === "broken");

  return (
    <AuthLayout>
      <form id="sharedChangePasswordForm" onSubmit={submit} noValidate className="space-y-5">
        <h1 id="sharedChangePasswordTitleText" className="text-page-title font-bold tracking-[-0.01em]">{forced ? "Choose your own password" : "Change your password"}</h1>
        {forced && (
          <InlineMessage kind="information">
            You logged in as {session.username} with a temporary password from an Admin. Choose your own password to continue.
          </InlineMessage>
        )}
        <MessagePlace id="sharedChangePasswordMessage" message={message} />
        <input id="sharedChangePasswordUsernameInput" type="text" autoComplete="username" value={session.username} readOnly hidden />

        <div className="flex flex-col gap-1">
          <label htmlFor="sharedChangePasswordCurrentPasswordInput" className="text-label font-semibold">
            {forced ? "Temporary password" : "Current password"}
          </label>
          <PasswordInput
            id="sharedChangePasswordCurrentPasswordInput"
            name="current-password"
            label={forced ? "Temporary password" : "Current password"}
            value={current}
            onChange={(v) => {
              setCurrent(v);
              setErrors((e) => ({ ...e, current: null }));
            }}
            autoComplete="current-password"
            describedBy={forced ? "sharedChangePasswordCurrentPasswordHelpText sharedChangePasswordCurrentPasswordErrorText" : "sharedChangePasswordCurrentPasswordErrorText"}
            invalid={invalid("current")}
            inputRef={(el) => (inputs.current.current = el)}
          />
          {forced && (
            <p id="sharedChangePasswordCurrentPasswordHelpText" className="text-small text-ink-muted">
              Type it exactly as you received it, with the hyphens.
            </p>
          )}
          <FieldError id="sharedChangePasswordCurrentPasswordErrorText" text={shown.current} />
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="sharedChangePasswordNewPasswordInput" className="text-label font-semibold">
            New password
          </label>
          <PasswordInput
            id="sharedChangePasswordNewPasswordInput"
            name="new-password"
            label="New password"
            value={next}
            onChange={(v) => {
              setNext(v);
              setErrors((e) => ({ ...e, next: null }));
            }}
            autoComplete="new-password"
            describedBy="sharedChangePasswordRuleText sharedChangePasswordNewPasswordErrorText"
            invalid={invalid("next")}
            inputRef={(el) => (inputs.current.next = el)}
          />
          <div className="mt-1.5 space-y-1.5">
            <div aria-hidden="true" className="grid grid-cols-4 gap-1.5">
              {[1, 2, 3, 4].map((step) => (
                <span
                  key={step}
                  className={cn("h-1.5 rounded-full", step <= LEVEL_STEPS[level] ? (LEVEL_STEPS[level] >= 3 ? "bg-done-fg" : "bg-waiting-dot") : "bg-track")}
                />
              ))}
            </div>
            <p className="flex flex-wrap justify-between gap-x-3 text-small">
              <span className="text-ink-muted">Avoid names and common words.</span>
              <span id="sharedMeasurePasswordStrengthText" className={cn("font-semibold", LEVEL_STEPS[level] >= 3 ? "text-done-fg" : level === "none" ? "text-ink-muted" : "text-waiting-fg")}>
                Strength: {LEVEL_WORD[level]}
              </span>
            </p>
          </div>
          <ul id="sharedChangePasswordRuleText" className="mt-1 space-y-1">
            <RuleLine state={ruleState(r.length, tooManyBytes || characters > 72)}>8 to 72 characters</RuleLine>
            <RuleLine state={ruleState(r.capital)}>At least one capital letter, A to Z</RuleLine>
            <RuleLine state={ruleState(r.small)}>At least one small letter, a to z</RuleLine>
            <RuleLine state={ruleState(r.digit)}>At least one digit, 0 to 9</RuleLine>
          </ul>
          <FieldError id="sharedChangePasswordNewPasswordErrorText" text={shown.next} />
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="sharedChangePasswordConfirmPasswordInput" className="text-label font-semibold">
            Repeat new password
          </label>
          <PasswordInput
            id="sharedChangePasswordConfirmPasswordInput"
            name="repeat-new-password"
            label="Repeated new password"
            value={repeat}
            onChange={(v) => {
              setRepeat(v);
              setErrors((e) => ({ ...e, repeat: null }));
            }}
            autoComplete="new-password"
            describedBy="sharedChangePasswordMatchText sharedChangePasswordConfirmPasswordErrorText"
            invalid={invalid("repeat")}
            inputRef={(el) => (inputs.current.repeat = el)}
          />
          <ul id="sharedChangePasswordMatchText" className="mt-1">
            <RuleLine state={matchState}>The same as the new password</RuleLine>
          </ul>
          <FieldError id="sharedChangePasswordConfirmPasswordErrorText" text={shown.repeat} />
        </div>

        <Button id="sharedChangePasswordSubmitButton" type="submit" level="main" busy={busy} disabled={loggingOut} busyText="Changing password…" className="w-full">
          Change password
        </Button>
        <div className="flex justify-center">
          {forced ? (
            <Button
              id="sharedChangePasswordLogoutButton"
              level="quiet"
              busy={loggingOut}
              disabled={busy}
              onClick={doLogout}
            >
              Log out
            </Button>
          ) : (
            <a id="sharedChangePasswordCancelLink" className={buttonClass("quiet")} href={session.startPage}>
              Cancel
            </a>
          )}
        </div>
      </form>
    </AuthLayout>
  );
}
