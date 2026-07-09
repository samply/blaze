/**
 * Per-resource-type summary mode settings.
 *
 * Summary mode maps to the FHIR `_summary=true` search parameter. It is enabled
 * by default. Users can toggle it in the UI; the choice is remembered per
 * resource type in a cookie so it survives navigation and server-side rendering.
 */

export const summaryCookieName = 'blaze.summary';

export interface SummarySettings {
  /** Default used for resource types without an explicit choice. */
  default: boolean;
  /** Explicit per-resource-type choices, keyed by resource type. */
  perType: Record<string, boolean>;
}

export const defaultSummarySettings: SummarySettings = {
  default: true,
  perType: {}
};

/**
 * Parses the summary settings from a cookie value.
 *
 * Falls back to {@link defaultSummarySettings} for missing or malformed input.
 */
export function parseSummarySettings(cookieValue: string | undefined): SummarySettings {
  if (!cookieValue) {
    return defaultSummarySettings;
  }
  try {
    const parsed = JSON.parse(cookieValue) as Partial<SummarySettings>;
    return {
      default: typeof parsed.default === 'boolean' ? parsed.default : true,
      perType: isBooleanRecord(parsed.perType) ? parsed.perType : {}
    };
  } catch {
    return defaultSummarySettings;
  }
}

/** Serializes the summary settings to a cookie value. */
export function serializeSummarySettings(settings: SummarySettings): string {
  return JSON.stringify(settings);
}

/**
 * Resolves whether summary mode is enabled for the given resource type,
 * falling back to the default when there is no explicit per-type choice.
 */
export function isSummaryEnabled(settings: SummarySettings, type?: string): boolean {
  if (type !== undefined && type in settings.perType) {
    return settings.perType[type];
  }
  return settings.default;
}

/** Returns new settings with the choice for the given resource type set. */
export function withSummaryForType(
  settings: SummarySettings,
  type: string,
  enabled: boolean
): SummarySettings {
  return {
    default: settings.default,
    perType: { ...settings.perType, [type]: enabled }
  };
}

/**
 * Resolves whether summary mode is enabled for the given resource type by
 * reading the summary settings from the parent (layout) load data.
 *
 * Centralizes the `await parent()` + {@link isSummaryEnabled} handling shared by
 * the bundle loaders.
 */
export async function loadSummary(
  parent: () => Promise<{ summarySettings: SummarySettings }>,
  type?: string
): Promise<boolean> {
  const { summarySettings } = await parent();
  return isSummaryEnabled(summarySettings, type);
}

/** One year in seconds, used as the summary cookie lifetime. */
const oneYearInSeconds = 60 * 60 * 24 * 365;

/**
 * Persists the summary settings in the browser cookie so they are available for
 * server-side rendering on the next request. Must run in the browser.
 */
export function storeSummarySettings(settings: SummarySettings): void {
  const value = encodeURIComponent(serializeSummarySettings(settings));
  document.cookie = `${summaryCookieName}=${value}; path=/; max-age=${oneYearInSeconds}; SameSite=Lax`;
}

function isBooleanRecord(value: unknown): value is Record<string, boolean> {
  return (
    typeof value === 'object' &&
    value !== null &&
    !Array.isArray(value) &&
    Object.values(value).every((v) => typeof v === 'boolean')
  );
}
