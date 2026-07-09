/**
 * Summary mode state of a single result set.
 *
 * `_summary` is absent exactly when full mode is on. The param appears to
 * request summary mode (`_summary=true`), count mode (`_summary=count`), or
 * to carry a value the control doesn't own (`text`, `data`, ...). Absent
 * means full, matching Blaze's own default, so every URL this app produces
 * stays copyable to curl. The `_summary` query param of the browser URL is
 * forwarded to the server verbatim and is the source of truth for a result
 * set, so a result set is shareable and the shown URL always matches the
 * request that produced it.
 *
 * App-generated links to search or history result pages request summary mode
 * by default (by appending `?_summary=true`), matching Blaze's
 * performance-oriented default for interactive use over large result sets.
 * This is independent of the URL-level default: an absent `_summary` still
 * means full, so a deep-linked or curled URL is unaffected. Choosing full
 * mode via the control removes the param again, making the mode explicit and
 * keeping the URL copyable to curl.
 */

/**
 * The three modes the result control can express.
 *
 * `summary` corresponds to `_summary=true`, `full` to an absent `_summary`
 * (Blaze's own default), and `count` to `_summary=count`.
 */
export type SummaryMode = 'summary' | 'full' | 'count';

/** The summary mode of a single result set. */
export interface SummaryState {
  /**
   * The summary mode of the result set, or undefined if the result set gives
   * no evidence of its summary mode, like a `_summary=text` result.
   */
  mode?: SummaryMode;
  /** Set if the mode of this result set can't be changed anymore. */
  fixed?: boolean;
}

/**
 * Resolves the summary state of a result set from the URL's `_summary` param.
 *
 * `true` means summary, absent or `false` means full (Blaze's own default),
 * `count` means count, and anything else (like `text`, `data`) leaves the
 * mode unknown.
 */
export function summaryFromUrl(params: URLSearchParams): SummaryState {
  const value = params.get('_summary');
  if (value === 'true') return { mode: 'summary' };
  if (value === 'count') return { mode: 'count' };
  if (value === null || value === 'false') return { mode: 'full' };
  return {};
}

/**
 * Returns the URL string with the `_summary` param set to reflect the given
 * mode.
 *
 * For `summary`, sets `_summary=true`. For `count`, sets `_summary=count`.
 * For `full`, deletes the param, since absent already means full (Blaze's
 * own default) and this keeps the URL copyable to curl.
 */
export function withSummaryParam(url: URL, mode: SummaryMode): string {
  const copy = new URL(url);
  if (mode === 'summary') {
    copy.searchParams.set('_summary', 'true');
  } else if (mode === 'count') {
    copy.searchParams.set('_summary', 'count');
  } else {
    copy.searchParams.delete('_summary');
  }
  return copy.toString();
}
