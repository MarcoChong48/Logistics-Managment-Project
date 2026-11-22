import { useCallback, useEffect, useState } from "react";
import { ApiError, getJson, sendJson } from "./http";
import type { ActionResult } from "./types";

export function adminAddress(path: string, params: Record<string, string | number | boolean | undefined>): string {
  const query = new URLSearchParams();
  for (const [name, value] of Object.entries(params)) {
    if (value !== undefined && value !== "") query.set(name, String(value));
  }
  const suffix = query.toString();
  return suffix ? `${path}?${suffix}` : path;
}

type ReadState<T> = { path: string; data?: T; error?: string; status?: number };

export function useAdminRead<T>(path: string | null) {
  const [result, setResult] = useState<ReadState<T>>();
  const [revision, setRevision] = useState(0);
  const refresh = useCallback(() => setRevision((previous) => previous + 1), []);

  useEffect(() => {
    if (!path) return;
    let active = true;
    getJson<T>(path).then(
      (data) => {
        if (active) setResult({ path, data });
      },
      (error: unknown) => {
        if (active) setResult({
          path,
          error: error instanceof Error ? error.message : "Could not load this data.",
          status: error instanceof ApiError ? error.status : undefined,
        });
      },
    );
    return () => { active = false; };
  }, [path, revision]);

  const current = path && result?.path === path ? result : undefined;
  return { data: current?.data, error: current?.error, status: current?.status, loading: Boolean(path && !current), refresh };
}

export function failedAction(error: unknown, fields: Record<string, string> = {}): ActionResult<never> {
  return {
    ok: false,
    message: error instanceof Error ? error.message : "Could not complete the request.",
    field: error instanceof ApiError ? (error.field ?? fields[error.code]) : undefined,
  };
}

export async function sendAdminMessage(method: string, path: string, body?: unknown, fields?: Record<string, string>): Promise<ActionResult> {
  try {
    const answer = await sendJson<{ message: string }>(method, path, body);
    return { ok: true, message: answer.message };
  } catch (error) {
    return failedAction(error, fields);
  }
}

export function parseTime(value: string | null): number | null {
  return value === null ? null : Date.parse(value);
}
