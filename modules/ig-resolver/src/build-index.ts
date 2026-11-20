// Builds the index of the resources of the guide from the resources SUSHI
// generates. Runs at build time, not in the Worker.

import type { Files, Index } from "./index-types.ts";

export type Resource = {
  resourceType: string;
  id: string;
  url?: string;
  uniqueId?: { type: string; value: string }[];
};

/**
 * Returns the files the IG Publisher renders a resource to. The page of the
 * ImplementationGuide itself is the index page of the guide.
 */
function renderedFiles({ resourceType, id }: Resource): Files {
  const name = `${resourceType}-${id}`;
  return {
    html:
      resourceType === "ImplementationGuide" ? "index.html" : `${name}.html`,
    json: `${name}.json`,
    xml: `${name}.xml`,
  };
}

function readPath({ resourceType, id }: Resource): string {
  return `${resourceType}/${id}`;
}

/**
 * Fails unless the url of a canonical resource is its read URL, so that every
 * canonical is covered by the index.
 */
function checkUrl(canonical: string, resource: Resource): void {
  const expected = `${canonical}/${readPath(resource)}`;
  if (resource.url !== undefined && resource.url !== expected) {
    throw new Error(
      `The url ${resource.url} of ${readPath(resource)} isn't ${expected}.`,
    );
  }
}

/**
 * Returns the paths, relative to `canonical`, of the URIs a NamingSystem
 * defines on `canonical`. A NamingSystem has no url of its own, so it is
 * identified by these URIs instead, like the system of an identifier.
 */
function uriPaths(canonical: string, { uniqueId = [] }: Resource): string[] {
  const prefix = `${canonical}/`;
  return uniqueId
    .filter(({ type, value }) => type === "uri" && value.startsWith(prefix))
    .map(({ value }) => value.slice(prefix.length));
}

function paths(canonical: string, resource: Resource): string[] {
  return resource.resourceType === "NamingSystem"
    ? [readPath(resource), ...uriPaths(canonical, resource)]
    : [readPath(resource)];
}

/**
 * Returns the index of `resources`, the resources of the guide with the
 * canonical base URL `canonical`.
 */
export function buildIndex(canonical: string, resources: Resource[]): Index {
  const index: Index = {
    basePath: new URL(canonical).pathname,
    resources: {},
  };
  for (const resource of resources) {
    checkUrl(canonical, resource);
    for (const path of paths(canonical, resource)) {
      if (Object.hasOwn(index.resources, path)) {
        throw new Error(`${path} occurs more than once.`);
      }
      index.resources[path] = renderedFiles(resource);
    }
  }
  return index;
}

/**
 * Returns the files of `index` for which `exists` is false, in index order.
 */
export function missingFiles(
  index: Index,
  exists: (file: string) => boolean,
): string[] {
  return Object.values(index.resources)
    .flatMap((files) => Object.values(files))
    .filter((file) => !exists(file));
}

/**
 * Returns the Worker routes covering the paths of `index`, one per first path
 * segment, the resource type of a read path. Routing per segment keeps the
 * pages and assets of the guide away from the Worker, so they don't count
 * against its request limit.
 */
export function routes(canonical: string, index: Index): string[] {
  const { host } = new URL(canonical);
  const segments = new Set(
    Object.keys(index.resources).map((path) => path.split("/")[0]),
  );
  return [...segments]
    .sort()
    .map((segment) => `${host}${index.basePath}/${segment}/*`);
}
