/**
 * Organization: Technosprint info Solutions
 * Owner: Logaraj S
 * Created at: 2026-09-10
 * Description: Install-drop and hold-to-uninstall gestures for the dashboard.
 */
import { useCallback, useEffect, useRef, useState } from "react";
import {
  floatingBinPositionFromCard,
  isPointerOverFloatingBin,
  type FloatingUninstallBinPosition,
} from "./floatingUninstallBin";

export interface DashboardGestures {
  isApplicationDragActive: boolean;
  isDropTargetActive: boolean;
  floatingUninstallBin: FloatingUninstallBinPosition | null;
  isUninstallBinHot: boolean;
  handleApplicationDragStart: (applicationId: string) => void;
  handleApplicationDragEnd: () => void;
  handleShowFloatingUninstallBin: (applicationId: string, cardRect: DOMRect) => void;
  setIsDropTargetActive: (isActive: boolean) => void;
}

/**
 * Owns dashboard drag and hold-to-uninstall state with listener cleanup.
 *
 * @param onUninstallApplication uninstall handler when the hold is released on the bin
 * @returns gesture flags and handlers
 */
export function useDashboardGestures(
  onUninstallApplication: (applicationId: string) => Promise<void>
): DashboardGestures {
  const [isApplicationDragActive, setIsApplicationDragActive] = useState(false);
  const [isDropTargetActive, setIsDropTargetActive] = useState(false);
  const [floatingUninstallBin, setFloatingUninstallBin] = useState<FloatingUninstallBinPosition | null>(null);
  const [isUninstallBinHot, setIsUninstallBinHot] = useState(false);
  const floatingUninstallBinRef = useRef<FloatingUninstallBinPosition | null>(null);
  const isUninstallBinHotRef = useRef(false);
  const onUninstallApplicationRef = useRef(onUninstallApplication);
  floatingUninstallBinRef.current = floatingUninstallBin;
  onUninstallApplicationRef.current = onUninstallApplication;

  const handleApplicationDragStart = useCallback((_applicationId: string) => {
    setIsApplicationDragActive(true);
  }, []);

  const handleApplicationDragEnd = useCallback(() => {
    setIsApplicationDragActive(false);
    setIsDropTargetActive(false);
  }, []);

  const handleShowFloatingUninstallBin = useCallback((applicationId: string, cardRect: DOMRect) => {
    const position = floatingBinPositionFromCard(cardRect);
    isUninstallBinHotRef.current = false;
    setFloatingUninstallBin({
      applicationId,
      top: position.top,
      left: position.left,
    });
    setIsUninstallBinHot(false);
  }, []);

  const hideFloatingUninstallBin = useCallback(() => {
    isUninstallBinHotRef.current = false;
    setFloatingUninstallBin(null);
    setIsUninstallBinHot(false);
  }, []);

  useEffect(() => {
    if (!floatingUninstallBin) {
      return;
    }
    const abortController = new AbortController();
    const listenerOptions: AddEventListenerOptions = { signal: abortController.signal };

    const handlePointerMove = (pointerEvent: PointerEvent) => {
      const currentBin = floatingUninstallBinRef.current;
      if (!currentBin) {
        return;
      }
      const isOverBin = isPointerOverFloatingBin(
        pointerEvent.clientX,
        pointerEvent.clientY,
        currentBin.top,
        currentBin.left
      );
      if (isOverBin === isUninstallBinHotRef.current) {
        return;
      }
      isUninstallBinHotRef.current = isOverBin;
      setIsUninstallBinHot(isOverBin);
    };

    const handlePointerRelease = (pointerEvent: PointerEvent) => {
      const currentBin = floatingUninstallBinRef.current;
      if (
        currentBin &&
        isPointerOverFloatingBin(pointerEvent.clientX, pointerEvent.clientY, currentBin.top, currentBin.left)
      ) {
        void onUninstallApplicationRef.current(currentBin.applicationId);
      }
      hideFloatingUninstallBin();
    };

    document.addEventListener("pointermove", handlePointerMove, listenerOptions);
    document.addEventListener("pointerup", handlePointerRelease, listenerOptions);
    document.addEventListener("pointercancel", handlePointerRelease, listenerOptions);
    return () => {
      abortController.abort();
    };
  }, [floatingUninstallBin, hideFloatingUninstallBin]);

  return {
    isApplicationDragActive,
    isDropTargetActive,
    floatingUninstallBin,
    isUninstallBinHot,
    handleApplicationDragStart,
    handleApplicationDragEnd,
    handleShowFloatingUninstallBin,
    setIsDropTargetActive,
  };
}
