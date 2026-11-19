// Answers a read request for a resource of the guide in the negotiated format.
//
// FHIR JSON and XML are answered in place, fetched from the origin serving the
// guide, so for a program the read URL stays the identity of the resource. A
// browser is redirected to the rendered page instead, because the page uses
// relative references to its assets and siblings, which break one directory
// below where it's rendered.

import type { Index } from "./index-types.ts";
import { type Format, negotiate } from "./negotiate.ts";
import { resolve } from "./resolve.ts";

export type FetchOrigin = (request: Request) => Promise<Response>;

/**
 * The FHIR media types of the formats the origin serves as plain JSON and XML.
 */
const fhirContentTypes: Partial<Record<Format, string>> = {
  json: "application/fhir+json;charset=utf-8",
  xml: "application/fhir+xml;charset=utf-8",
};

function methodNotAllowed(): Response {
  return new Response("Method Not Allowed", {
    status: 405,
    headers: { Allow: "GET, HEAD" },
  });
}

function notFound(): Response {
  return new Response("Not Found", { status: 404 });
}

/**
 * Redirects to the rendered page at `location`, varying on `Accept` because the
 * same URL answers FHIR JSON and XML in place.
 */
function redirectToPage(location: string): Response {
  return new Response(null, {
    status: 302,
    headers: { Location: location, Vary: "Accept" },
  });
}

/**
 * Returns the response of the origin with the FHIR media type of `format` on
 * success, varying on `Accept` because the same URL answers every format.
 */
function negotiated(origin: Response, format: Format): Response {
  const response = new Response(origin.body, origin);
  const contentType = fhirContentTypes[format];
  if (origin.ok && contentType !== undefined) {
    response.headers.set("Content-Type", contentType);
  }
  response.headers.append("Vary", "Accept");
  return response;
}

export type Deps = {
  index: Index;
  /** The origin serving the rendering, like `https://blaze-server.org`. */
  origin: string;
  fetchOrigin: FetchOrigin;
};

/**
 * Answers `request` from `index`, fetching the rendered file from `origin`
 * with `fetchOrigin`.
 */
export async function handle(
  request: Request,
  { index, origin, fetchOrigin }: Deps,
): Promise<Response> {
  if (request.method !== "GET" && request.method !== "HEAD") {
    return methodNotAllowed();
  }
  const url = new URL(request.url);
  const files = resolve(index, url.pathname);
  if (files === undefined) return notFound();

  const format = negotiate(
    request.headers.get("Accept"),
    url.searchParams.get("_format"),
  );
  const fileUrl = new URL(`${index.basePath}/${files[format]}`, origin);
  if (format === "html") return redirectToPage(fileUrl.href);
  return negotiated(await fetchOrigin(new Request(fileUrl, request)), format);
}
