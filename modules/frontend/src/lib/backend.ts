import { base } from '$app/paths';

/**
 * Builds the URL of a backend request from `path` and an optional `query`.
 *
 * This is the only way backend request URLs are built. It is deliberately
 * distinct from `resolve` of `$app/paths`, which builds navigation targets
 * from route IDs: the two kinds of URL are built from different inputs and
 * are not always equal, so a call site should say which of the two it means.
 *
 * The result is root-relative on purpose. `handleFetch` in `hooks.server.ts`
 * swaps this app's origin for `BACKEND_BASE_URL` and keeps the path, so the
 * base path is part of the backend URL as well, and an absolute URL would have
 * to duplicate that knowledge.
 *
 * `base` is deprecated in favor of `resolve`, but `resolve` cannot serve this
 * case, which is why this stays the single place that reads it:
 * - Some backend paths are not routes of this app at all — there is none for
 *   `/$totals` or `/__admin/Task/{id}/$pause`. That the generated `Pathname`
 *   type admits them anyway is an accident of the `[type=type]` routes
 *   widening it to `/${string}`.
 * - On the server `resolve` honors `paths.relative`, which defaults to true,
 *   and then returns a path relative to the page being rendered. Its depth is
 *   computed from the URL the browser is at, which for a `__data.json` request
 *   has one segment more than the `event.url` that `event.fetch` resolves
 *   against, so a resolved backend URL would silently drop the base path.
 */
export function backendUrl(path: string, query?: URLSearchParams | string): string {
  const search = query?.toString();
  return search ? `${base}${path}?${search}` : `${base}${path}`;
}
