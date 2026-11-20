import type { Format } from "./negotiate.ts";

/**
 * The rendered file of a resource of the guide in every format.
 */
export type Files = Record<Format, string>;

/**
 * Maps the read path `<resourceType>/<id>` of every resource of the guide, and
 * the URIs a NamingSystem defines, relative to `basePath`, to its rendered
 * files.
 */
export type Index = {
  basePath: string;
  resources: Record<string, Files>;
};
