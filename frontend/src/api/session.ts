import { useSyncExternalStore } from "react";
import { ApiError, getJson, sendJson, subscribeToApiResponses } from "./http";
import type { ActionResult, Role } from "./types";

const SESSION_ENDPOINT = "/api/shared/session";
const LOGIN_ENDPOINT = "/api/shared/login";
const LOGOUT_ENDPOINT = "/api/shared/logout";
const CHANGE_PASSWORD_ENDPOINT = "/api/shared/change-password";
const EXTEND_ENDPOINT = "/api/shared/session/extend";

export const LOGIN_PAGE = "/shared/shared-login.html";
export const CHANGE_PASSWORD_PAGE = "/shared/shared-change-password.html";
export const WELCOME_PAGE = "/shared/shared-welcome.html";

export type LoginNotice = "IDLE" | "LOGGED_OUT" | "SESSION_ENDED" | "PASSWORD_CHANGED" | "PASSWORD_NOT_CHANGED";

interface SessionResponse {
  sharedViewSessionIsLoggedIn: boolean;
  userID: number | null;
  username: string | null;
  role: Role | null;
  mustChangePassword: boolean | null;
  sharedViewSessionIdleTimeoutSeconds: number;
  sharedViewSessionTestModeIsEnabled: boolean;
  sharedViewSessionNextPageWebAddress: string | null;
}

export interface LiveSession {
  userID: number;
  username: string;
  role: Role;
  mustChangePassword: boolean;
  idleTimeoutSeconds: number;
  testModeIsEnabled: boolean;
  startPage: string;
}

type SessionState =
  | { status: "loading" }
  | { status: "ready"; session: LiveSession | null }
  | { status: "error"; message: string };

let state: SessionState = { status: "loading" };
let deadline = 0;
let pending: Promise<void> | undefined;
const sessionListeners = new Set<() => void>();
const deadlineListeners = new Set<() => void>();

function saveSession(next: SessionState): void {
  state = next;
  sessionListeners.forEach((listener) => listener());
}

function setDeadline(next: number): void {
  if (next === deadline) return;
  deadline = next;
  deadlineListeners.forEach((listener) => listener());
}

function subscribeSession(listener: () => void): () => void {
  sessionListeners.add(listener);
  return () => sessionListeners.delete(listener);
}

function subscribeDeadline(listener: () => void): () => void {
  deadlineListeners.add(listener);
  return () => deadlineListeners.delete(listener);
}

export function useSessionState(): SessionState {
  return useSyncExternalStore(subscribeSession, () => state);
}

export function useLiveSession(): LiveSession | null {
  const current = useSessionState();
  return current.status === "ready" ? current.session : null;
}

export function readLiveSession(): LiveSession | null {
  return state.status === "ready" ? state.session : null;
}

export function useSessionDeadline(): number {
  return useSyncExternalStore(subscribeDeadline, () => deadline);
}

export function restartSessionTimer(): void {
  const session = readLiveSession();
  if (session) setDeadline(Date.now() + session.idleTimeoutSeconds * 1000);
}

export function stopSessionTimer(): void {
  setDeadline(0);
}

export function loginAddress(notice?: LoginNotice): string {
  return notice ? `${LOGIN_PAGE}?sharedLoginUserNotice=${notice}` : LOGIN_PAGE;
}

export function welcomeAddress(accessDenied = false): string {
  return accessDenied ? `${WELCOME_PAGE}?sharedShowWelcomeNotice=ACCESS_DENIED` : WELCOME_PAGE;
}

function startPage(address: string | null): string {
  if (!address) return WELCOME_PAGE;
  try {
    const url = new URL(address, window.location.origin);
    if (url.origin !== window.location.origin || url.pathname.startsWith("//")) return WELCOME_PAGE;
    return `${url.pathname}${url.search}${url.hash}`;
  } catch {
    return WELCOME_PAGE;
  }
}

function endSession(notice?: LoginNotice): void {
  stopSessionTimer();
  const address = loginAddress(notice);
  if (`${location.pathname}${location.search}` !== address) window.location.replace(address);
}

export function expireSession(): void {
  endSession("IDLE");
}

export function loadLiveSession(): Promise<void> {
  if (pending) return pending;
  saveSession({ status: "loading" });
  pending = getJson<SessionResponse>(SESSION_ENDPOINT).then((answer) => {
    const session = answer.sharedViewSessionIsLoggedIn && answer.userID !== null && answer.username !== null && answer.role
      ? {
          userID: answer.userID,
          username: answer.username,
          role: answer.role,
          mustChangePassword: Boolean(answer.mustChangePassword),
          idleTimeoutSeconds: answer.sharedViewSessionIdleTimeoutSeconds,
          testModeIsEnabled: answer.sharedViewSessionTestModeIsEnabled,
          startPage: startPage(answer.sharedViewSessionNextPageWebAddress),
        }
      : null;
    saveSession({ status: "ready", session });
    if (session) restartSessionTimer();
    else stopSessionTimer();
  }).catch((error: unknown) => {
    stopSessionTimer();
    saveSession({ status: "error", message: errorMessage(error) });
  }).finally(() => {
    pending = undefined;
  });
  return pending;
}

/** Match the accepted shared page redirects and refresh the countdown on server activity. */
export function observeSessionRequests(): () => void {
  return subscribeToApiResponses(({ method, path, status, code }) => {
    if (path === LOGIN_ENDPOINT || status === 0) return;
    if (status === 401 && (code === "SHARED_NOT_LOGGED_IN" || code === "SHARED_SESSION_ENDED")) {
      const savingPassword = path === CHANGE_PASSWORD_ENDPOINT && method === "POST";
      endSession(savingPassword ? "PASSWORD_NOT_CHANGED" : code === "SHARED_SESSION_ENDED" ? "SESSION_ENDED" : undefined);
      return;
    }
    if (status === 403 && code === "SHARED_PASSWORD_CHANGE_REQUIRED") {
      window.location.replace(CHANGE_PASSWORD_PAGE);
      return;
    }
    restartSessionTimer();
  });
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "The server could not complete the request.";
}

export async function login(username: string, password: string): Promise<ActionResult<{ next: string }>> {
  try {
    if (pending) await pending;
    const answer = await sendJson<{ sharedLoginUserNextPageWebAddress: string }>("POST", LOGIN_ENDPOINT, {
      sharedLoginUserUsername: username,
      sharedLoginUserPassword: password,
    });
    return { ok: true, message: "Logged in.", data: { next: startPage(answer.sharedLoginUserNextPageWebAddress) } };
  } catch (error) {
    return { ok: false, message: errorMessage(error), field: username.trim() ? "password" : "username" };
  }
}

async function sessionAction(path: string): Promise<ActionResult> {
  try {
    const answer = await sendJson<{ message: string }>("POST", path);
    return { ok: true, message: answer.message };
  } catch (error) {
    return { ok: false, message: errorMessage(error) };
  }
}

export async function logout(): Promise<ActionResult> {
  const answer = await sessionAction(LOGOUT_ENDPOINT);
  if (answer.ok) endSession("LOGGED_OUT");
  return answer;
}

export function extendSession(): Promise<ActionResult> {
  return sessionAction(EXTEND_ENDPOINT);
}

const PASSWORD_ERROR_FIELDS: Record<string, string> = {
  SHARED_CURRENT_PASSWORD_WRONG: "current",
  SHARED_PASSWORD_RULE_FAILED: "new",
  SHARED_CONFIRM_PASSWORD_DIFFERENT: "confirm",
};

export async function changePassword(current: string, next: string, repeat: string): Promise<ActionResult> {
  try {
    const answer = await sendJson<{ message: string }>("POST", CHANGE_PASSWORD_ENDPOINT, {
      sharedChangePasswordCurrentPassword: current,
      sharedChangePasswordNewPassword: next,
      sharedChangePasswordConfirmPassword: repeat,
    });
    endSession("PASSWORD_CHANGED");
    return { ok: true, message: answer.message };
  } catch (error) {
    const message = errorMessage(error);
    if (error instanceof ApiError) {
      if (error.status === 400) return { ok: false, message, field: PASSWORD_ERROR_FIELDS[error.code] };
      if (error.status === 401 && (error.code === "SHARED_SESSION_ENDED" || error.code === "SHARED_NOT_LOGGED_IN")) {
        return { ok: false, message, field: "session" };
      }
      if (error.status === 403 && error.code === "SHARED_SECURITY_TOKEN_INVALID") {
        return { ok: false, message: "This page is out of date. Reload it and try again. Your password was not changed." };
      }
    }
    return {
      ok: false,
      message: message + " It is not known whether your password was changed. Log out, then log in with your new password. If it is refused, use your old password.",
    };
  }
}
