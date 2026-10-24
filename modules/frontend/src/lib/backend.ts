import { resolve } from '$app/paths';

/**
 * Builds the URL of a backend request from `path` and an optional `query`.
 *
 * This is the only way backend request URLs are built. It is deliberately
 * distinct from navigation targets, which are built with `resolve` of
 * `$app/paths` from route IDs: the two kinds of URL are built from different
 * inputs and are not always equal, so a call site should say which of the two
 * it means.
 *
 * The result is root-relative on purpose. `handleFetch` in `hooks.server.ts`
 * swaps this app's origin for `BACKEND_BASE_URL` and keeps the path, so the
 * base path is part of the backend URL as well, and an absolute URL would have
 * to duplicate that knowledge.
 *
 * Some backend paths are not routes of this app at all — there is none for
 * `/$totals` or `/__admin/Task/{id}/$pause` — so the path is resolved as a
 * pathname rather than a route ID.
 *
 * `resolve` returns root-relative URLs only because `paths.relative` is
 * disabled. Otherwise, on the server it would return a path relative to the
 * page being rendered. Its depth is computed from the URL the browser is at,
 * which for a `__data.json` request has one segment more than the `event.url`
 * that `event.fetch` resolves against, so a resolved backend URL would
 * silently drop the base path.
 */
export function backendUrl(path: string, query?: URLSearchParams | string): string {
  const search = query?.toString();
  const pathname = path.slice(1);
  return resolve(search ? `${pathname}?${search}` : pathname);
}
