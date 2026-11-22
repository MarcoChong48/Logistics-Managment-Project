import { useEffect, type RefObject } from "react";

/**
 * After a refused save, focus moves to the first field marked invalid, so a keyboard or screen reader user
 * lands on what needs fixing. Inside an open window the search stays in that window; a page form passes its
 * own box as `root`. Nothing moves when the refusal names no field (the message box says what happened).
 */
export function useFocusFirstInvalid(trigger: unknown, root?: RefObject<HTMLElement | null>) {
  useEffect(() => {
    if (!trigger) return;
    const box: ParentNode = root?.current ?? document.querySelector("dialog[open]") ?? document;
    box.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus();
  }, [trigger, root]);
}
