import type { Element } from 'fhir/r4';

const dataAbsentReasonUrl = 'http://hl7.org/fhir/StructureDefinition/data-absent-reason';

/**
 * Returns the reason code of the data-absent-reason extension of `element`,
 * or `undefined` if it has none.
 */
export function dataAbsentReason(element: Element | undefined): string | undefined {
  return element?.extension?.find((e) => e.url === dataAbsentReasonUrl)?.valueCode;
}

/**
 * Whether `element` has extensions other than the data-absent-reason extension.
 */
export function hasOtherExtensions(element: Element | undefined): boolean {
  return element?.extension?.some((e) => e.url !== dataAbsentReasonUrl) ?? false;
}
