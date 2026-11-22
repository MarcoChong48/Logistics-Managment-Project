import { useCallback, useEffect, useRef, useState } from "react";
import { useSearchParams } from "react-router";

/**
 * List filters kept in the address, so a link from the dashboard opens a filtered list and the browser's
 * Back button returns to the same filters. Changing a filter goes back to page 1.
 *
 * The values live in local state first and are copied to the address afterwards. The router applies
 * address changes as a transition, so reading inputs straight from the address could make a tick box or
 * a search field jump back for a moment while someone types.
 */
export function useListParams<T extends Record<string, string>>(defaults: T) {
  const [params, setParams] = useSearchParams();
  const urlKey = params.toString();
  const read = useCallback(
    (source: URLSearchParams) => Object.fromEntries(Object.entries(defaults).map(([name, fallback]) => [name, source.get(name) ?? fallback])) as T,
    // The defaults are written once per page.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );
  const [values, setValues] = useState<T>(() => read(params));
  const latest = useRef(values);
  latest.current = values;
  const written = useRef(urlKey);

  // The address changed from outside (a link, Back, Forward): take its values.
  useEffect(() => {
    if (urlKey !== written.current) {
      written.current = urlKey;
      setValues(read(new URLSearchParams(urlKey)));
    }
  }, [urlKey, read]);

  const write = useCallback(
    (next: T) => {
      const search = new URLSearchParams();
      for (const [name, value] of Object.entries(next)) {
        if (value !== "" && value !== defaults[name]) search.set(name, value);
      }
      written.current = search.toString();
      setParams(search, { replace: true });
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [setParams],
  );

  // The new values are worked out first, then the state and the address are set one after the other
  // (never the address inside a state update, which React refuses while it renders).
  const set = useCallback(
    (patch: Partial<T>) => {
      const next = { ...latest.current, ...patch } as T;
      if (!("page" in patch) && "page" in defaults) (next as Record<string, string>).page = (defaults as Record<string, string>).page;
      latest.current = next;
      setValues(next);
      write(next);
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [write],
  );

  const clear = useCallback(() => {
    setValues(defaults);
    write(defaults);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [write]);

  return { values, set, clear };
}
