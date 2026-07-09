import { SearchParamType } from '$lib/fhir.js';
import { defaultCount } from '$lib/util.js';

export interface QueryParam {
  id: number;
  active: boolean;
  name: string;
  type: SearchParamType;
  value: string;
}

export function selectParam(id: number): QueryParam {
  return {
    id,
    active: true,
    name: '__select',
    type: 'special' as unknown as SearchParamType,
    value: ''
  };
}

function removeInactiveModifier(name: string): [string, boolean] {
  const active = !name.endsWith(':inactive');
  return [active ? name : name.substring(0, name.length - 9), active];
}

/**
 * Initialises the query-param rows from the URL's search params.
 *
 * Returns the rows and the query-plan flag separately. Hides `_summary` and
 * `_count` (when at the default) from the rows, because they are managed by
 * dedicated controls or the app itself.
 */
export function initQueryParams(urlSearchParams: URLSearchParams): {
  queryParams: QueryParam[];
  queryPlan: boolean;
} {
  const queryParams: QueryParam[] = [];
  let queryPlan = false;
  for (const [name, value] of urlSearchParams) {
    if (name == '__explain') {
      queryPlan = value == 'true';
      continue;
    }
    if (name.startsWith('__')) {
      continue;
    }
    if (name == '_summary') {
      continue;
    }
    if (name == '_count' && value == defaultCount) {
      continue;
    }
    const [paramName, active] = removeInactiveModifier(name);
    queryParams.push({
      id: queryParams.length,
      active,
      name: paramName,
      type: 'composite' as unknown as SearchParamType,
      value
    });
  }
  if (queryParams.length == 0) {
    queryParams.push(selectParam(queryParams.length));
  }
  return { queryParams, queryPlan };
}

/**
 * Builds the query params for a search submission.
 *
 * Emits `_summary` from a single writer: the URL's current value, carried
 * over verbatim. This prevents duplicates when the URL already carries
 * `_summary`, e.g. from a previous search or a link the app rendered.
 */
export function submitParams(
  queryParams: QueryParam[],
  queryPlan: boolean,
  urlSummary: string | null
): URLSearchParams {
  const params = queryParams
    .filter((p) => p.name != '__select')
    .map((p) => ({ ...p, value: p.value.trim() }))
    .filter((p) => p.value.length != 0)
    .map((p) => [p.active ? p.name : p.name + ':inactive', p.value] as [string, string]);
  if (queryPlan) params.push(['__explain', 'true']);
  if (urlSummary !== null) {
    params.push(['_summary', urlSummary]);
  }
  return new URLSearchParams(params);
}
