/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Starts an action after a press is held, and clears the timer on unmount.
 */
import { useCallback, useEffect, useMemo, useRef, type PointerEvent } from "react";
import { DASHBOARD_LONG_PRESS_MS, PRIMARY_POINTER_BUTTON } from "@kernel/dashboard/dashboardConstants";

interface LongPressHandlers {
  onPointerDown: (pointerEvent: PointerEvent<HTMLElement>) => void;
  onPointerUp: () => void;
  onPointerCancel: () => void;
}

/**
 * Returns pointer handlers that fire after a long press.
 *
 * @param onLongPress callback when the hold completes
 * @param isDisabled when true, the timer never starts
 * @returns pointer handlers with unmount cleanup
 */
export function useLongPress(onLongPress: () => void, isDisabled: boolean): LongPressHandlers {
  const longPressTimerRef = useRef<number | null>(null);
  const onLongPressRef = useRef(onLongPress);
  onLongPressRef.current = onLongPress;

  const clearLongPressTimer = useCallback(() => {
    if (longPressTimerRef.current !== null) {
      window.clearTimeout(longPressTimerRef.current);
      longPressTimerRef.current = null;
    }
  }, []);

  useEffect(() => {
    return () => {
      clearLongPressTimer();
    };
  }, [clearLongPressTimer]);

  const handlePointerDown = useCallback(
    (pointerEvent: PointerEvent<HTMLElement>) => {
      if (isDisabled || pointerEvent.button !== PRIMARY_POINTER_BUTTON) {
        return;
      }
      clearLongPressTimer();
      longPressTimerRef.current = window.setTimeout(() => {
        longPressTimerRef.current = null;
        onLongPressRef.current();
      }, DASHBOARD_LONG_PRESS_MS);
    },
    [clearLongPressTimer, isDisabled]
  );

  return useMemo(
    () => ({
      onPointerDown: handlePointerDown,
      onPointerUp: clearLongPressTimer,
      onPointerCancel: clearLongPressTimer,
    }),
    [clearLongPressTimer, handlePointerDown]
  );
}
