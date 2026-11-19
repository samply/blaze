import type { Files, Index } from "./index-types.ts";

/**
 * Returns the files of the resource at `pathname`, or undefined if no resource
 * of the guide has that read path.
 */
export function resolve(index: Index, pathname: string): Files | undefined {
  const prefix = `${index.basePath}/`;
  if (!pathname.startsWith(prefix)) return undefined;
  const path = pathname.slice(prefix.length);
  return Object.hasOwn(index.resources, path)
    ? index.resources[path]
    : undefined;
}
