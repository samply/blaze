import table from 'virtual:element-types';

/**
 * The type of an element of a resource.
 */
export interface ElementType {
  /** The FHIR type code, e.g. `date`, `HumanName` or `BackboneElement`. */
  code: string;

  /** The target profiles of an element of type `canonical`. */
  targetProfile?: string[];

  /**
   * The name of a choice-typed element without its type suffix, e.g. `value`
   * for `valueQuantity`.
   */
  choiceName?: string;
}

/**
 * The element types of the top-level elements of resources, by resource type
 * and JSON property name. Choice-typed elements have one entry per type.
 */
export type ElementTypes = Record<string, Record<string, ElementType>>;

const elementTypes = table;

/**
 * Returns the type of the top-level element of a resource of `resourceType`
 * having the JSON property `name`, or `undefined` if there is no such element.
 */
export function elementType(resourceType: string, name: string): ElementType | undefined {
  return Object.hasOwn(elementTypes, resourceType) &&
    Object.hasOwn(elementTypes[resourceType], name)
    ? elementTypes[resourceType][name]
    : undefined;
}

/**
 * Whether `type` is a primitive type. The codes of primitive types start with
 * a lower case letter.
 */
export function isPrimitive(type: ElementType): boolean {
  return type.code[0].toLowerCase() == type.code[0];
}
