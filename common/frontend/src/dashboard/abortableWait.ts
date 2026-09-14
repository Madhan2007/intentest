/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Promise delay that clears its timer when the caller aborts.
 */

/**
 * Waits for a duration, or resolves immediately when the signal aborts.
 *
 * @param durationMs delay in milliseconds
 * @param abortSignal cancellation signal
 * @returns promise that settles when the delay ends or is aborted
 */
export function abortableWait(durationMs: number, abortSignal: AbortSignal): Promise<void> {
  return new Promise((resolve) => {
    if (abortSignal.aborted) {
      resolve();
      return;
    }
    const timeoutId = window.setTimeout(resolve, durationMs);
    abortSignal.addEventListener(
      "abort",
      () => {
        window.clearTimeout(timeoutId);
        resolve();
      },
      { once: true }
    );
  });
}
