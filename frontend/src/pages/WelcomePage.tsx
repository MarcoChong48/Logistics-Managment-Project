import { useState } from "react";
import { AuthLayout } from "./AuthLayout";
import { Button, buttonClass, MessagePlace } from "../components/ui";
import { CHANGE_PASSWORD_PAGE, WELCOME_PAGE, logout, type LiveSession } from "../api/session";
import { roleLabel } from "../lib/status";

/** The page a person sees when their own start page is not built yet, or when a page was refused. */
export function WelcomePage({ session }: { session: LiveSession }) {
  const [error, setError] = useState<string | null>(null);
  const [loggingOut, setLoggingOut] = useState(false);
  const refused = new URLSearchParams(window.location.search).get("sharedShowWelcomeNotice") === "ACCESS_DENIED";
  const message = error ?? (refused ? "You are not allowed to open that page." : null);
  const hasStartPage = session.startPage !== WELCOME_PAGE;

  const doLogout = async () => {
    setLoggingOut(true);
    setError(null);
    const answer = await logout();
    if (!answer.ok) {
      setLoggingOut(false);
      setError(answer.message);
    }
  };

  return (
    <AuthLayout>
      <div className="space-y-5">
        <div>
          <h1 id="sharedShowWelcomeUsernameText" className="text-page-title font-bold tracking-[-0.01em]">
            Welcome, {session.username}
          </h1>
          <p id="sharedShowWelcomeRoleText" className="mt-1 text-ink-muted">
            Role: {roleLabel(session.role)}
          </p>
        </div>
        <MessagePlace id="sharedShowWelcomeNoticeMessage" message={message ? { kind: "error", text: message } : null} />
        {hasStartPage && (
          <a id="sharedShowWelcomeStartPageLink" href={session.startPage} className={buttonClass("main", false, "w-full")}>
            Back to your start page
          </a>
        )}
        <div className="flex flex-wrap items-center justify-center gap-3">
          <a id="sharedShowWelcomeChangePasswordLink" href={CHANGE_PASSWORD_PAGE} className="inline-flex min-h-10 items-center rounded-control px-2 text-label font-semibold text-teal hover:bg-teal-tint hover:text-teal-hover">
            Change password
          </a>
          <Button id="sharedShowWelcomeLogoutButton" small busy={loggingOut} onClick={doLogout}>
            Log out
          </Button>
        </div>
      </div>
    </AuthLayout>
  );
}
