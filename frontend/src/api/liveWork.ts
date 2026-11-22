import { useEffect, useState } from "react";
import { ApiError, sendJson } from "./http";
import type { ActionResult } from "./types";

export const apiTime = (value?: string | null): number | null => value ? Date.parse(value) : null;

export function apiPath(path: string, params: Record<string, string | number | boolean> = {}): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value !== "") search.set(key, String(value));
  }
  const query = search.toString();
  return query ? `${path}?${query}` : path;
}

export interface WorkPage<T> {
  rows: T[];
  page: number;
  pageCount: number;
  total: number;
}

export function useWorkQuery<T>(load: () => Promise<T>) {
  const [generation, setGeneration] = useState(0);
  const [response, setResponse] = useState<{ load: typeof load; generation: number; data?: T; error?: string }>();
  useEffect(() => {
    let current = true;
    void load().then(
      (data) => { if (current) setResponse({ load, generation, data }); },
      (error: unknown) => {
        if (current) setResponse({ load, generation, error: error instanceof Error ? error.message : "The server could not be reached." });
      },
    );
    return () => { current = false; };
  }, [load, generation]);
  const result = response?.load === load ? response : undefined;
  return {
    data: result?.data,
    error: result?.generation === generation ? result.error : undefined,
    pending: result?.generation !== generation,
    refresh: () => setGeneration((n) => n + 1),
  };
}

export async function workAction<Dto, Data>(
  path: string,
  body: unknown,
  read: (dto: Dto) => ActionResult<Data>,
): Promise<ActionResult<Data>> {
  try {
    return read(await sendJson<Dto>("POST", path, body));
  } catch (error) {
    const conflict = error instanceof ApiError && error.code === "ADMIN_CLIENT_INSTRUCTION_STATE_CONFLICT";
    return {
      ok: false,
      message: error instanceof Error ? error.message : "The server could not be reached.",
      field: conflict ? "conflict" : undefined,
    };
  }
}

export interface DecidedRow {
  message: string;
  adminDecidedRowStateWord: string;
  adminDecidedRowWorkerUsername: string | null;
  adminDecidedRowDecidedByUsername: string;
  adminDecidedRowDecidedTime: string;
  refundID: number | null;
  adminDecidedRowRefundIsNew: boolean;
}
