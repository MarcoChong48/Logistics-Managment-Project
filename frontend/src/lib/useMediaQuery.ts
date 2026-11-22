import { useSyncExternalStore } from "react";

/** True while the media query matches, for example useMediaQuery("(min-width: 768px)"). */
export function useMediaQuery(query: string): boolean {
  return useSyncExternalStore(
    (onChange) => {
      const list = window.matchMedia(query);
      list.addEventListener("change", onChange);
      return () => list.removeEventListener("change", onChange);
    },
    () => window.matchMedia(query).matches,
    () => false,
  );
}

/** Real tables from 768 px up; row cards below. */
export function useWideTables(): boolean {
  return useMediaQuery("(min-width: 768px)");
}

/** Work tables (their rows carry decision buttons) from 1280 px up, where they fit beside the side menu. */
export function useWideWorkTables(): boolean {
  return useMediaQuery("(min-width: 1280px)");
}

/** The fixed side menu from 1024 px up; a drawer below. */
export function useDesktopFrame(): boolean {
  return useMediaQuery("(min-width: 1024px)");
}
