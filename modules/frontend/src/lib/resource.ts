import type { CodeSystem, FhirResource, ValueSet } from 'fhir/r5';

export function title(resource: FhirResource) {
  if (resource.resourceType === 'CodeSystem') {
    const codeSystem = resource as CodeSystem;
    if (codeSystem.title && codeSystem.version) {
      return `${codeSystem.title} v${codeSystem.version}`;
    }
  }

  if (resource.resourceType === 'ValueSet') {
    const valueSet = resource as ValueSet;
    if (valueSet.title && valueSet.version) {
      return `${valueSet.title} v${valueSet.version}`;
    }
  }

  return `${resource.resourceType}/${resource.id}`;
}
