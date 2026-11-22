const SESSION_PATH = "/api/shared/session";
const LOGIN_PATH = "/api/shared/login";
const SESSION_CHANGING_PATHS = new Set([LOGIN_PATH, "/api/shared/logout", "/api/shared/change-password"]);

interface SessionTokenResponse {
  sharedViewSessionSecurityToken?: string;
  sharedViewSessionSecurityTokenHeaderName?: string;
}

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
    public readonly code: string,
    public readonly field?: string,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export interface ApiResponseEvent {
  method: string;
  path: string;
  status: number;
  code?: string;
}

let securityHeader: { name: string; value: string } | undefined;
let tokenRequest: Promise<unknown> | undefined;
const responseListeners = new Set<(event: ApiResponseEvent) => void>();

export function subscribeToApiResponses(listener: (event: ApiResponseEvent) => void) {
  responseListeners.add(listener);
  return () => { responseListeners.delete(listener); };
}

function report(event: ApiResponseEvent) {
  for (const listener of responseListeners) listener(event);
}

async function ensureToken(refresh = false) {
  if (securityHeader && !refresh) return;
  tokenRequest ??= getJson<SessionTokenResponse>(SESSION_PATH).finally(() => {
    tokenRequest = undefined;
  });
  await tokenRequest;
  if (!securityHeader) {
    throw new ApiError("The server did not provide a security token. Reload the page and try again.", 0, "NO_SECURITY_TOKEN");
  }
}

async function request(method: string, path: string, body?: unknown, canRetryToken = true): Promise<Response> {
  const headers = new Headers({ Accept: "application/json, text/csv" });
  const changesData = !["GET", "HEAD", "OPTIONS"].includes(method);
  if (changesData) {
    await ensureToken(path === LOGIN_PATH);
    if (securityHeader) headers.set(securityHeader.name, securityHeader.value);
  }
  const payload = body instanceof FormData ? body : body == null ? undefined : JSON.stringify(body);
  if (typeof payload === "string") headers.set("Content-Type", "application/json");

  let response: Response;
  try {
    response = await fetch(path, {
      method, headers, body: payload, credentials: "same-origin", cache: "no-store", redirect: "error",
    });
  } catch {
    throw new ApiError("Could not reach the server. Check your connection and try again.", 0, "NETWORK_ERROR");
  }

  if (!response.ok) {
    const error = await response.json().catch(() => null);
    const code = typeof error?.code === "string" ? error.code : "UNEXPECTED_ERROR";
    if (changesData && canRetryToken && code === "SHARED_SECURITY_TOKEN_INVALID") {
      securityHeader = undefined;
      return request(method, path, body, false);
    }
    if (response.status === 401 && path !== LOGIN_PATH) securityHeader = undefined;
    report({ method, path, status: response.status, code });
    throw new ApiError(error?.message ?? "The server could not complete the request.", response.status, code, error?.field);
  }

  if (changesData && SESSION_CHANGING_PATHS.has(path)) securityHeader = undefined;
  report({ method, path, status: response.status });
  return response;
}

async function readJson<T>(method: string, path: string, body?: unknown): Promise<T> {
  const response = await request(method, path, body);
  if (response.status === 204) return undefined as T;
  let result: T;
  try {
    result = await response.json();
  } catch {
    throw new ApiError("The server returned an unreadable response.", response.status, "INVALID_RESPONSE");
  }
  if (path === SESSION_PATH) {
    const session = result as SessionTokenResponse;
    const name = session.sharedViewSessionSecurityTokenHeaderName;
    const value = session.sharedViewSessionSecurityToken;
    securityHeader = name && value ? { name, value } : undefined;
  }
  return result;
}

export function getJson<T>(path: string): Promise<T> {
  return readJson<T>("GET", path);
}

export function sendJson<T>(method: string, path: string, body?: unknown): Promise<T> {
  return readJson<T>(method.toUpperCase(), path, body);
}

export async function downloadCsv(path: string, filename = "export.csv"): Promise<void> {
  const response = await request("GET", path);
  if (!response.headers.get("Content-Type")?.toLowerCase().startsWith("text/csv")) {
    throw new ApiError("The server did not return a CSV file.", response.status, "INVALID_RESPONSE");
  }
  const disposition = response.headers.get("Content-Disposition");
  const serverFilename = disposition?.match(/filename="?([^";]+)"?/i)?.[1];
  const url = URL.createObjectURL(await response.blob());
  const link = document.createElement("a");
  link.href = url;
  link.download = serverFilename || filename;
  document.body.append(link);
  link.click();
  link.remove();
  window.setTimeout(() => URL.revokeObjectURL(url), 1_000);
}
