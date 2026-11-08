import type { OperationOutcome } from 'fhir/r4';
import type { RouteParams } from './$types.js';
import { backendUrl } from '#lib/backend.js';

async function diagnostics(res: Response): Promise<string | undefined> {
  try {
    return ((await res.json()) as OperationOutcome).issue?.[0]?.diagnostics;
  } catch {
    return undefined;
  }
}

/**
 * Builds the error body for an unsuccessful response of reading the resource
 * or executing the $graph operation on it.
 *
 * On 400, the diagnostics of the OperationOutcome are used as message, because
 * they explain what's wrong with the request, e.g. a GraphDefinition whose
 * start node doesn't fit the type of the resource. Retrying the same request
 * won't help in that case.
 *
 * The status is left out because `error` adds it.
 */
export async function appError(
  params: RouteParams,
  res: Response
): Promise<Omit<App.Error, 'status'>> {
  switch (res.status) {
    case 400:
      return {
        short: 'Bad Request',
        message: (await diagnostics(res)) ?? 'Please check the selected GraphDefinition.'
      };
    case 404:
      return {
        short: 'Not Found',
        message: `The ${params.type} with ID ${params.id} was not found.`
      };
    case 410:
      return {
        short: 'Gone',
        message: `The ${params.type} with ID ${params.id} was deleted. Please look into the history.`
      };
    default:
      return {
        short: undefined,
        message: `An error happened while loading the ${params.type} with ID ${params.id}. Please try again later.`
      };
  }
}

/**
 * Builds the backend URL of the $graph operation on the resource with the
 * canonical `graph` of the GraphDefinition.
 *
 * The canonical is encoded, so that characters like `+`, `&`, `#` and `%` of
 * its URL or version arrive unchanged.
 */
export function graphUrl(params: RouteParams, graph: string): string {
  return backendUrl(`/${params.type}/${params.id}/$graph`, new URLSearchParams({ graph }));
}

/**
 * The resource types which don't inherit from `DomainResource`.
 */
const nonDomainResourceTypes = ['Binary', 'Bundle', 'Parameters'];

/**
 * Returns the start types of GraphDefinitions that can start at a resource of
 * `type`: the type itself, `DomainResource` if `type` inherits from it, and
 * `Resource`.
 */
export function startTypes(type: string): string[] {
  return nonDomainResourceTypes.includes(type)
    ? [type, 'Resource']
    : [type, 'DomainResource', 'Resource'];
}

/**
 * Returns the query of the search for the GraphDefinitions that can start at a
 * resource of `type`.
 *
 * The search param `start` searches the type of the start resource, which is
 * the type of the start node.
 */
export function graphDefinitionsQuery(type: string): URLSearchParams {
  return new URLSearchParams({ start: startTypes(type).join(','), _summary: 'true' });
}
