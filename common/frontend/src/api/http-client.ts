/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz React coding standards.
 */
import { createApiError } from "@kernel/api/errors";

/** Kernel response envelope (doc 04 §4). */
interface Envelope<T> {
  ok: boolean;
  data: T | null;
  error: { code: string; kind: string; msg: string; hint?: string; fields?: unknown } | null;
  correlation_id: string;
}

const API_PREFIX = {
  ai: "/api/v1/ai",
  opzhub: "/api/v1/opzhub",
} as const;

const HTTP_DEFAULTS = {
  credentials: "include" as const,
  jsonContentType: "application/json",
  postMethod: "POST",
} as const;

const UNKNOWN_API_ERROR = {
  code: "unknown_error",
  kind: "unknown_error",
} as const;

function buildJsonHeaders(headers?: HeadersInit): HeadersInit {
  return {
    "Content-Type": HTTP_DEFAULTS.jsonContentType,
    ...(headers ?? {}),
  };
}

function buildPath(prefix: string, path: string): string {
  return `${prefix}${path}`;
}

function buildPostBody(body?: unknown): string | undefined {
  return body ? JSON.stringify(body) : undefined;
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    credentials: HTTP_DEFAULTS.credentials,
    headers: buildJsonHeaders(init?.headers),
  });
  if (init?.signal?.aborted) {
    throw new DOMException("The operation was aborted.", "AbortError");
  }
  const body = (await response.json().catch(() => null)) as Envelope<T> | null;
  if (!body || !body.ok) {
    const err = body?.error;
    throw createApiError({
      code: err?.code ?? UNKNOWN_API_ERROR.code,
      kind: err?.kind ?? UNKNOWN_API_ERROR.kind,
      msg: err?.msg ?? `Request failed (${response.status})`,
      hint: err?.hint,
      fields: err?.fields,
      correlationId: body?.correlation_id ?? "",
    });
  }
  return body.data as T;
}

/** HTTP client to the Java ERP engine, same-origin via Nginx/Vite proxy (doc 03 §2). */
export const httpOpzhub = {
  get: <T>(path: string, init?: RequestInit) =>
    request<T>(buildPath(API_PREFIX.opzhub, path), init),
  post: <T>(path: string, body?: unknown, init?: RequestInit) =>
    request<T>(buildPath(API_PREFIX.opzhub, path), {
      ...init,
      method: HTTP_DEFAULTS.postMethod,
      body: buildPostBody(body),
    }),
};

/** HTTP client to the Python AI engine (doc 03 §2). Unused until an AI-backed module is packed. */
export const httpAi = {
  get: <T>(path: string, init?: RequestInit) =>
    request<T>(buildPath(API_PREFIX.ai, path), init),
  post: <T>(path: string, body?: unknown, init?: RequestInit) =>
    request<T>(buildPath(API_PREFIX.ai, path), {
      ...init,
      method: HTTP_DEFAULTS.postMethod,
      body: buildPostBody(body),
    }),
};
