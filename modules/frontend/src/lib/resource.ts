import type { CodeSystem, FhirResource, ValueSet } from 'fhir/r4';

/**
 * Returns the human readable title of `resource`, including its version, or
 * undefined if the resource has no such title.
 */
export function versionedTitle(resource: FhirResource): string | undefined {
  if (resource.resourceType === 'CodeSystem' || resource.resourceType === 'ValueSet') {
    const { title, version } = resource as CodeSystem | ValueSet;
    if (title && version) {
      return `${title} v${version}`;
    }
  }

  return undefined;
}

/**
 * Returns a name of `resource` that identifies it on its own, either its
 * versioned title or its type and id.
 */
export function title(resource: FhirResource): string {
  return versionedTitle(resource) ?? `${resource.resourceType}/${resource.id}`;
}
