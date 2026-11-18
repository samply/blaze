// Content negotiation of the representation of a resource of the guide.
//
// `_format` wins over `Accept`, as in FHIR R4 (http.html#mime-type), so that a
// client unable to set `Accept`, like a browser following a link, can still ask
// for FHIR JSON or XML.

export type Format = "html" | "json" | "xml";

const mediaTypeFormats = new Map<string, Format>([
  ["text/html", "html"],
  ["application/xhtml+xml", "html"],
  ["application/fhir+json", "json"],
  ["application/json", "json"],
  ["application/fhir+xml", "xml"],
  ["application/xml", "xml"],
  ["text/xml", "xml"],
]);

const acceptFormats = new Map<string, Format>([
  ...mediaTypeFormats,
  ["*/*", "html"],
]);

const formatParamFormats = new Map<string, Format>([
  ...mediaTypeFormats,
  ["html", "html"],
  ["json", "json"],
  ["xml", "xml"],
]);

/**
 * Returns the media type of `value` without parameters, lowercase.
 */
function mediaType(value: string): string {
  return (value.split(";")[0] ?? "").trim().toLowerCase();
}

/**
 * Returns the q-value of one entry of an `Accept` header, 1 if absent and 0 if
 * malformed.
 */
function qValue(entry: string): number {
  for (const parameter of entry.split(";").slice(1)) {
    const [name, value] = parameter.split("=").map((part) => part.trim());
    if (name?.toLowerCase() === "q") {
      const q = Number(value);
      return Number.isNaN(q) ? 0 : q;
    }
  }
  return 1;
}

/**
 * Returns the format an `Accept` header asks for: the known media type with
 * the highest q-value, the earlier one on equal q-values. HTML if the header
 * is absent or names no known media type.
 */
function formatOfAccept(accept: string | null): Format {
  let best: { format: Format; q: number } | undefined;
  for (const entry of (accept ?? "").split(",")) {
    const format = acceptFormats.get(mediaType(entry));
    const q = qValue(entry);
    if (format !== undefined && q > 0 && (best === undefined || q > best.q)) {
      best = { format, q };
    }
  }
  return best?.format ?? "html";
}

/**
 * Returns `format` with a space read as `+`. A query string decodes an
 * unencoded `+` as a space, so a hand-written `_format=application/fhir+json`
 * arrives as `application/fhir json`.
 */
function restorePlus(format: string): string {
  return format.trim().replaceAll(" ", "+");
}

/**
 * Returns the format a `_format` parameter asks for, or undefined if it's
 * absent or unknown.
 */
function formatOfParam(format: string | null): Format | undefined {
  return format === null
    ? undefined
    : formatParamFormats.get(mediaType(restorePlus(format)));
}

/**
 * Returns the format to answer with, given the `Accept` header and the
 * `_format` parameter of a request.
 */
export function negotiate(
  accept: string | null,
  format: string | null,
): Format {
  return formatOfParam(format) ?? formatOfAccept(accept);
}
