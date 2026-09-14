/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-01
 * Description: Governed by ManageMyOpz React coding standards.
 */
interface ApiErrorOptions {
  code: string;
  kind: string;
  msg: string;
  hint?: string;
  fields?: unknown;
  correlationId: string;
}

/**
 * Thrown when a kernel API request resolves to an application-level error.
 * @param opts Normalized error details from the API envelope.
 * @returns A typed error instance that preserves backend metadata.
 */
export class ApiError extends Error {
  code: string;
  kind: string;
  hint?: string;
  fields?: unknown;
  correlationId: string;

  constructor(opts: ApiErrorOptions) {
    super(opts.msg);
    this.code = opts.code;
    this.kind = opts.kind;
    this.hint = opts.hint;
    this.fields = opts.fields;
    this.correlationId = opts.correlationId;
  }
}

/**
 * Creates a normalized API error from backend envelope details.
 * @param opts Raw error data mapped from the HTTP response envelope.
 * @returns A typed `ApiError` instance for shared frontend handling.
 */
export function createApiError(opts: ApiErrorOptions): ApiError {
  return new ApiError(opts);
}

/**
 * Checks whether an unknown thrown value is an `ApiError`.
 * @param error Candidate thrown value.
 * @returns `true` when the value is an `ApiError` instance.
 */
export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}
