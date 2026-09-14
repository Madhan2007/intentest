/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Detects fetch/Web abort so callers can skip setState after unmount.
 */

const ABORT_ERROR_NAME = "AbortError";

/**
 * Returns true when a failed request was cancelled on purpose.
 *
 * @param error rejection from fetch or AbortController
 * @returns whether the error is an abort
 */
export function isAbortError(error: unknown): boolean {
  if (error instanceof DOMException) {
    return error.name === ABORT_ERROR_NAME;
  }
  return (
    typeof error === "object" &&
    error !== null &&
    "name" in error &&
    (error as { name: string }).name === ABORT_ERROR_NAME
  );
}
