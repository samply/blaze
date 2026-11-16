// Version handling of the transaction load test history charts.
//
// A version is either a release like `1.11.0` or the commit hash of a
// development build. Each system has at most one commit run, which is always
// the newest version. This module is pure and free of Vite APIs, so it can be
// tested with Node directly.
import type { LegendEntry } from "./plot.ts";

const RELEASE = /^[0-9]+\.[0-9]+\.[0-9]+$/;

/** The maximum number of versions that can be selected at once. */
const MAX_SELECTED = 2;

/** Returns whether `version` is a release, otherwise it is a commit hash. */
export function isRelease(version: string): boolean {
  return RELEASE.test(version);
}

function releaseParts(version: string): number[] {
  return version.split(".").map(Number);
}

/**
 * Sorts `versions` from oldest to newest.
 *
 * Releases are ordered by their numeric major, minor and patch parts, so
 * `1.10.0` comes after `1.9.0`. The commit run comes last. More than one
 * commit run is an error, because only one development build can be the
 * newest.
 */
export function sortVersions(versions: string[]): string[] {
  const releases = versions.filter(isRelease);
  const commits = versions.filter((version) => !isRelease(version));
  if (commits.length > 1) {
    throw new Error(`more than one commit run: ${commits.join(", ")}`);
  }
  releases.sort((a, b) => {
    const [pa, pb] = [releaseParts(a), releaseParts(b)];
    return pa[0] - pb[0] || pa[1] - pb[1] || pa[2] - pb[2];
  });
  return [...releases, ...commits];
}

/**
 * Returns `selected` after the user clicked `version`.
 *
 * `selected` is in selection order. Clicking a selected version deselects it,
 * except if it is the only one, because at least one version has to stay
 * selected. Clicking another version selects it. Selecting a third version
 * drops the one selected first.
 */
export function select(selected: string[], version: string): string[] {
  if (selected.includes(version)) {
    return selected.length === 1
      ? selected
      : selected.filter((v) => v !== version);
  }
  return [...selected, version].slice(-MAX_SELECTED);
}

/** Returns `selected` sorted by the position in the sorted `versions`. */
export function inVersionOrder(
  selected: string[],
  versions: string[],
): string[] {
  return [...selected].sort(
    (a, b) => versions.indexOf(a) - versions.indexOf(b),
  );
}

/**
 * Asserts that every `values` entry of the data file `file` equals `version`.
 *
 * Guards against a file that was copied by hand and would otherwise be shown
 * under the wrong version.
 */
export function assertVersion(
  file: string,
  version: string,
  values: string[],
): void {
  if (values.some((value) => value !== version)) {
    throw new Error(
      `${file} holds results of ${values.join(", ")} instead of ${version}`,
    );
  }
}

/**
 * Returns the legend entries naming the compared `versions` by line style.
 *
 * All versions but the newest are dashed. The entries draw no marker, because
 * the marker would cover the gap of the dash, so that a dashed entry would look
 * like a solid one.
 */
export function versionLegend(versions: string[]): LegendEntry[] {
  const last = versions.length - 1;
  return versions.map((version, index) => ({
    name: version,
    color: "var(--blaze-chart-line-reference)",
    shape: "line",
    marker: false,
    ...(index < last ? { dashed: true } : {}),
  }));
}
