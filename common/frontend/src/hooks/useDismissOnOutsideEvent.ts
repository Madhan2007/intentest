/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Closes a floating panel on outside pointer or Escape, with listener cleanup.
 */
import { useEffect, useRef, type RefObject } from "react";

const POINTER_EVENT_NAME = "pointerdown";
const KEYBOARD_EVENT_NAME = "keydown";
const ESCAPE_KEY = "Escape";

/**
 * Dismisses an open panel when the user clicks outside it or presses Escape.
 * Listeners are bound with AbortController so they cannot survive unmount.
 *
 * @param isOpen whether the panel is visible
 * @param panelRootRef element that owns the panel
 * @param onDismiss close handler
 */
export function useDismissOnOutsideEvent(
  isOpen: boolean,
  panelRootRef: RefObject<HTMLElement | null>,
  onDismiss: () => void
): void {
  const onDismissRef = useRef(onDismiss);
  onDismissRef.current = onDismiss;

  useEffect(() => {
    if (!isOpen) {
      return;
    }

    const abortController = new AbortController();
    const listenerOptions: AddEventListenerOptions = { signal: abortController.signal };

    const handlePointerDown = (pointerEvent: PointerEvent) => {
      const panelRoot = panelRootRef.current;
      const eventTarget = pointerEvent.target;
      if (
        panelRoot &&
        eventTarget instanceof Node &&
        !panelRoot.contains(eventTarget)
      ) {
        onDismissRef.current();
      }
    };

    const handleKeyDown = (keyboardEvent: KeyboardEvent) => {
      if (keyboardEvent.key === ESCAPE_KEY) {
        onDismissRef.current();
      }
    };

    document.addEventListener(POINTER_EVENT_NAME, handlePointerDown, listenerOptions);
    document.addEventListener(KEYBOARD_EVENT_NAME, handleKeyDown, listenerOptions);
    return () => {
      abortController.abort();
    };
  }, [isOpen, panelRootRef]);
}
