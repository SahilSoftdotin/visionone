import { useEffect, useLayoutEffect, useRef, useState, type ReactNode, type RefObject } from 'react';
import { createPortal } from 'react-dom';

const GAP = 6;
const MENU_MAX_HEIGHT = 288; // matches max-h-72 on the menus

/**
 * A dropdown panel rendered at the top of the document, anchored to its trigger.
 *
 * Why a portal: every page section uses the `reveal` entrance animation, whose fill-mode leaves a
 * transform behind and so gives each section its own stacking context. A menu rendered inside a
 * section can never rise above the section after it, whatever its z-index - which is how the month
 * pickers ended up hidden behind the panel below. Rendering at the body escapes every stacking
 * context and every overflow clip, so the menu's placement on the page stops mattering.
 *
 * Owns closing too: a click outside both the trigger and the menu, Escape, or the page resizing.
 * The menu is not inside the trigger's wrapper any more, so an outside-click check written against
 * the wrapper alone would close the menu before an option's click could land.
 */
export function FloatingMenu({
  anchorRef,
  open,
  onClose,
  align = 'right',
  children,
}: {
  anchorRef: RefObject<HTMLElement | null>;
  open: boolean;
  onClose: () => void;
  align?: 'left' | 'right';
  children: ReactNode;
}) {
  const menuRef = useRef<HTMLDivElement>(null);
  const [style, setStyle] = useState<React.CSSProperties>({ visibility: 'hidden' });

  useLayoutEffect(() => {
    if (!open) return;
    const place = () => {
      const anchor = anchorRef.current;
      if (!anchor) return;
      const rect = anchor.getBoundingClientRect();
      const spaceBelow = window.innerHeight - rect.bottom;
      // Open upwards only when there is clearly more room above; downwards is what people expect.
      const openUp = spaceBelow < MENU_MAX_HEIGHT + GAP && rect.top > spaceBelow;
      setStyle({
        position: 'fixed',
        zIndex: 60,
        ...(openUp
          ? { bottom: window.innerHeight - rect.top + GAP }
          : { top: rect.bottom + GAP }),
        ...(align === 'right'
          ? { right: Math.max(8, window.innerWidth - rect.right) }
          : { left: Math.max(8, rect.left) }),
      });
    };
    place();
    // Capture phase, so scrolling any ancestor - not just the window - keeps the menu attached.
    window.addEventListener('scroll', place, true);
    window.addEventListener('resize', place);
    return () => {
      window.removeEventListener('scroll', place, true);
      window.removeEventListener('resize', place);
    };
  }, [open, align, anchorRef]);

  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      const target = e.target as Node;
      if (anchorRef.current?.contains(target) || menuRef.current?.contains(target)) return;
      onClose();
    };
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose();
    };
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [open, onClose, anchorRef]);

  if (!open) return null;
  return createPortal(
    <div ref={menuRef} style={style}>
      {children}
    </div>,
    document.body,
  );
}
