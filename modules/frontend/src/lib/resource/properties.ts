import type { Element, Resource } from 'fhir/r4';
import { elementType, isPrimitive, type ElementType } from '#lib/element-types.js';

/**
 * A top-level element of a resource.
 */
export interface Property {
  /** The JSON property name, e.g. `valueQuantity`. */
  name: string;

  /** The name to show, which is the name without type suffix for choice-typed elements. */
  humanName: string;

  type: ElementType;

  /**
   * The values, also for elements with a single value. A primitive value with
   * extensions only, like a data-absent-reason, is `undefined`.
   */
  values: unknown[];

  /**
   * The elements holding the id and extensions of primitive values, at the
   * index of their value. Comes from the JSON property `_<name>`.
   */
  elements: (Element | undefined)[];
}

function toArray<T>(x: T | T[] | undefined): T[] {
  return x === undefined ? [] : Array.isArray(x) ? x : [x];
}

/**
 * Returns the top-level elements of `resource` in JSON order. Elements unknown
 * to the type of the resource are skipped.
 *
 * Primitive elements are also returned if they have no value but only the
 * JSON property `_<name>`, holding their extensions.
 */
export function properties(resource: Resource): Property[] {
  const json = resource as unknown as Record<string, unknown>;
  return Object.keys(json).flatMap((key) => {
    const extensionsOnly = key.startsWith('_');
    const name = extensionsOnly ? key.substring(1) : key;
    if (extensionsOnly && Object.hasOwn(json, name)) {
      return [];
    }
    const type = elementType(resource.resourceType, name);
    if (type === undefined || (extensionsOnly && !isPrimitive(type))) {
      return [];
    }
    const elements = toArray(json['_' + name] as Element | (Element | null)[] | undefined).map(
      (e) => e ?? undefined
    );
    return [
      {
        name,
        humanName: type.choiceName ?? name,
        type,
        values: extensionsOnly ? elements.map(() => undefined) : toArray(json[name]),
        elements
      }
    ];
  });
}
