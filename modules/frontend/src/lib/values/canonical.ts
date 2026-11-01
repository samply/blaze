import type { ElementType } from '#lib/element-types.js';

const targetProfilePrefix = 'http://hl7.org/fhir/StructureDefinition/';

/**
 * Returns the resource type the canonical `type` points to, or `undefined` if
 * its target profile is missing or isn't one of a core resource type.
 */
export function targetType(type: ElementType): string | undefined {
  const targetProfile = type.targetProfile?.[0];
  return targetProfile !== undefined && targetProfile.startsWith(targetProfilePrefix)
    ? targetProfile.substring(targetProfilePrefix.length)
    : undefined;
}
