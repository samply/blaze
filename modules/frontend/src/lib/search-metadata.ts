import type { CapabilityStatement, CapabilityStatementRestResourceSearchParam } from 'fhir/r4';
import { sortByProperty } from '$lib/util.js';

/**
 * Everything the search form needs to offer for one resource type.
 *
 * All three values are read off the same CapabilityStatement, are constant
 * for a resource type and so can be loaded once per page and passed down as
 * props.
 */
export interface SearchMetadata {
  /** the search params of the type, including the server-level ones, sorted by name */
  searchParams: CapabilityStatementRestResourceSearchParam[];
  /** the values offered for `_include` */
  searchIncludes: string[];
  /** the values offered for `_revinclude` */
  searchRevIncludes: string[];
}

/**
 * Returns search metadata offering nothing, for pages without a loaded
 * CapabilityStatement.
 *
 * A function instead of a constant, so callers can't mutate a shared instance.
 */
export function emptySearchMetadata(): SearchMetadata {
  return { searchParams: [], searchIncludes: [], searchRevIncludes: [] };
}

/**
 * Extracts the search metadata of the resource type `type` from the given
 * CapabilityStatement.
 *
 * Only the first `rest` entry is considered, because Blaze describes its
 * server capabilities there. Missing entries yield empty lists, so an
 * unknown resource type simply offers nothing.
 */
export function searchMetadata(
  capabilityStatement: CapabilityStatement,
  type: string
): SearchMetadata {
  const server = capabilityStatement.rest?.at(0);
  const resource = server?.resource?.find((r) => r.type === type);

  return {
    searchParams: [...(server?.searchParam ?? []), ...(resource?.searchParam ?? [])].sort(
      sortByProperty('name')
    ),
    searchIncludes: resource?.searchInclude ?? [],
    searchRevIncludes: resource?.searchRevInclude ?? []
  };
}
