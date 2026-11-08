import type { CapabilityStatement } from 'fhir/r4';

/**
 * The instance operations having a page in the frontend, in the order they are
 * shown. Operations without `types` have a page for every resource type.
 */
const operationsWithPage: { name: string; types?: string[] }[] = [
  { name: 'lookup', types: ['CodeSystem'] },
  { name: 'expand', types: ['ValueSet'] },
  { name: 'validate-code', types: ['CodeSystem', 'ValueSet'] },
  { name: 'graph' }
];

/**
 * Returns the names of the operations on instances of `type`, which the
 * server supports according to `capabilityStatement`, and which have a page in
 * the frontend.
 */
export function instanceOperations(
  capabilityStatement: CapabilityStatement,
  type: string
): string[] {
  const supported = new Set(
    capabilityStatement.rest?.[0]?.resource
      ?.find((resource) => resource.type === type)
      ?.operation?.map((operation) => operation.name) ?? []
  );
  return operationsWithPage
    .filter(
      ({ name, types }) => (types === undefined || types.includes(type)) && supported.has(name)
    )
    .map(({ name }) => name);
}
