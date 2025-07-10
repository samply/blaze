/* eslint-disable @typescript-eslint/no-explicit-any */

import type {
  Address,
  Attachment,
  Bundle,
  BundleEntry,
  CodeableConcept,
  Coding,
  Element,
  ElementDefinition,
  ElementDefinitionType,
  Extension,
  FhirResource,
  HumanName,
  Identifier,
  Meta,
  Money,
  Period,
  Quantity,
  Resource,
  StructureDefinition
} from 'fhir/r4';
import { startsWithUpperCase, toTitleCase } from '$lib/util.js';
import { fetchStructureDefinition } from '../metadata.js';

export function isPrimitive(type: Type) {
  return type.code[0].toLowerCase() == type.code[0];
}

export interface FhirObjectBundle extends Bundle {
  fhirObjectEntry?: FhirObjectBundleEntry[];
}

export interface FhirObjectBundleEntry extends BundleEntry {
  fhirObject?: FhirObject;
}

export interface Type {
  code: string;
  targetProfile?: string[];
}

export interface FhirType {
  type: Type;
}

export type FhirComplexType =
  | Address
  | Attachment
  | Identifier
  | Meta
  | Money
  | Period
  | Quantity
  | Coding
  | CodeableConcept
  | HumanName;

/**
 * A structured representation of a Resource with ordered properties and type annotations.
 */
export interface FhirObject<ObjectType = FhirComplexType | FhirResource> extends FhirType {
  properties: FhirProperty[];
  object: ObjectType;
}

/**
 * A named property. The name includes the type for polymorph properties.
 */
export interface FhirProperty {
  name: string;
  humanName?: string; // name without the type, if undefined, use name
  type: Type;
  value: FhirObject[] | FhirObject | FhirPrimitive[] | FhirPrimitive;
}

export interface FhirPrimitive extends FhirType {
  value: string | number | boolean;
  extensions?: FhirObject<Extension>[];
}

export async function transformBundle(
  fetch: typeof window.fetch,
  bundle: Bundle
): Promise<FhirObjectBundle> {
  return bundle.entry !== undefined
    ? {
        ...bundle,
        fhirObjectEntry: await Promise.all(
          bundle.entry.map(async (e: BundleEntry) =>
            e.resource !== undefined ? { ...e, fhirObject: await fhirObject(e.resource, fetch) } : e
          )
        )
      }
    : bundle;
}

type getTypeElementsFunction = (
  typeElements: ElementDefinition[],
  parentPath: string | undefined,
  path: string
) => ElementDefinition[];

export function getTypeElements(cache: Map<string, ElementDefinition[]>): getTypeElementsFunction {
  return (typeElements: ElementDefinition[], parentPath: string | undefined, path: string) => {
    const key = parentPath === undefined ? path : parentPath + '.' + path;

    const result = cache.get(key);

    if (result !== undefined) {
      return result;
    }

    const newResult = typeElements
      .filter((e) => e.path.startsWith(path) && e.path != path)
      .map((e) => ({ ...e, path: dropSegments(e.path, path.split('.').length - 1) }));
    cache.set(key, newResult);

    return newResult;
  };
}

function onlyUnique(value: string, index: number, array: string[]): boolean {
  return array.indexOf(value) === index;
}

function typeNames(elements: ElementDefinition[]): string[] {
  return elements
    .flatMap((e) => e.type ?? [])
    .map((t) => t.code)
    .filter(startsWithUpperCase)
    .filter((c) => c !== 'BackboneElement')
    .filter((c) => c !== 'Element')
    .filter((c) => c !== 'Resource')
    .filter(onlyUnique);
}

async function fetchAllStructureDefinitions(
  type: string,
  knownTypes: Set<string>,
  fetch: typeof window.fetch
): Promise<Map<string, StructureDefinition>> {
  const structureDefinition = await fetchStructureDefinition(type, fetch);

  const map = new Map([[type, structureDefinition]]);

  const elements = structureDefinition.snapshot?.element;
  if (elements === undefined) {
    return map;
  }

  for (const t of typeNames(elements).filter((t) => t !== type && !knownTypes.has(t))) {
    if (map.has(t)) continue;

    const kTs = new Set([...knownTypes, ...map.keys()]);
    const typeMap = await fetchAllStructureDefinitions(t, kTs, fetch);
    typeMap.forEach((value, key) => map.set(key, value));
  }

  return map;
}

/**
 * Returns a Promise of a FhirObject of the given Resource.
 *
 * @param resource the Resource to use
 * @param fetch the fetch function to use, defaults to window.fetch
 * @returns the FhirObject representation of the given Resource
 */
export async function fhirObject(
  resource: FhirResource,
  fetch: typeof window.fetch = window.fetch
): Promise<FhirObject> {
  const structureDefinitions = await fetchAllStructureDefinitions(
    resource.resourceType,
    new Set(),
    fetch
  );

  return calcPropertiesDeep(
    (type) => structureDefinitions.get(type) as StructureDefinition,
    getTypeElements(new Map<string, ElementDefinition[]>()),
    structureDefinitions.get(resource.resourceType) as StructureDefinition,
    resource
  );
}

function onlyType(element: ElementDefinition): Type | undefined {
  return element?.type?.length == 1 ? element.type[0] : undefined;
}

export function calcPropertiesDeep(
  fetchStructureDefinition: (type: string) => StructureDefinition,
  getTypeElements: getTypeElementsFunction,
  structureDefinition: StructureDefinition,
  resource: FhirResource | Element
): FhirObject {
  const elements = structureDefinition.snapshot?.element || [];
  const properties = elements
    .filter((element) => element.path.split('.').length === 2)
    .map((element) =>
      processElement(
        undefined,
        element,
        elements,
        elements,
        resource,
        fetchStructureDefinition,
        getTypeElements
      )
    )
    .filter((p) => p !== undefined)
    .map((p) => p as FhirProperty);

  return {
    type: { code: structureDefinition.name },
    properties:
      structureDefinition.kind == 'resource'
        ? [
            {
              name: 'resourceType',
              type: { code: 'string' },
              value: { type: { code: 'string' }, value: structureDefinition.name as string }
            },
            ...properties
          ]
        : properties,
    object: resource
  };
}

function dropSegments(path: string, n: number): string {
  return path.split('.').slice(n).join('.');
}

function processElement(
  parentPath: string | undefined,
  element: ElementDefinition,
  resourceElements: ElementDefinition[],
  typeElements: ElementDefinition[],
  resource: (Resource | Element) & { [key: string]: any },
  fetchStructureDefinition: (type: string) => StructureDefinition,
  getTypeElements: getTypeElementsFunction
): FhirProperty | undefined {
  // If this is a contentReference, resolve it
  if (element.contentReference !== undefined) {
    const id = element.contentReference.substring(1);
    const referencedElement = resourceElements.find((e) => e.id === id);
    if (referencedElement === undefined) {
      return undefined;
    } else {
      const name = element.path.split('.')[1];
      const value = resource[name];

      if (value === undefined) {
        return undefined;
      }

      return processAbstractElement(
        referencedElement.path.split('.')[0],
        name,
        value,
        resourceElements,
        getTypeElements(resourceElements, undefined, referencedElement.path),
        { code: 'BackboneElement' },
        fetchStructureDefinition,
        getTypeElements
      );
    }
  }

  const type = onlyType(element);

  if (type?.code == 'Element' || type?.code == 'BackboneElement') {
    const name = element.path.split('.')[1];
    const value = resource[name];

    if (value === undefined) {
      return undefined;
    }

    return processAbstractElement(
      parentPath === undefined
        ? element.path.split('.')[0]
        : parentPath + '.' + element.path.split('.')[0],
      name,
      value,
      resourceElements,
      getTypeElements(typeElements, parentPath, element.path),
      type,
      fetchStructureDefinition,
      getTypeElements
    );
  } else if (type?.code === 'Resource') {
    const name = element.path.split('.')[1];
    const value = resource[name];

    if (value === undefined) {
      return undefined;
    }

    return processResourceElement(name, value, fetchStructureDefinition, getTypeElements);
  } else {
    return processTypedElement(element, resource, fetchStructureDefinition, getTypeElements);
  }
}

function mapType(type: ElementDefinitionType): Type {
  const t = {
    code: type.code == 'http://hl7.org/fhirpath/System.String' ? 'string' : type.code
  };
  return type.targetProfile !== undefined ? { ...t, targetProfile: type.targetProfile } : t;
}

function processResourceElement(
  name: string,
  value: Resource | Resource[],
  fetchStructureDefinition: (type: string) => StructureDefinition,
  getTypeElements: getTypeElementsFunction
): FhirProperty | undefined {
  return {
    name: name,
    type: { code: 'Resource' },
    value: Array.isArray(value)
      ? (value as Resource[]).map((v) =>
          processResourceValue(v, fetchStructureDefinition, getTypeElements)
        )
      : processResourceValue(value, fetchStructureDefinition, getTypeElements)
  };
}

function processResourceValue(
  resource: Resource,
  fetchStructureDefinition: (type: string) => StructureDefinition,
  getTypeElements: getTypeElementsFunction
): FhirObject {
  const structureDefinition = fetchStructureDefinition(resource.resourceType);
  return calcPropertiesDeep(
    fetchStructureDefinition,
    getTypeElements,
    structureDefinition,
    resource
  );
}

// Processes one of the abstract elements. Type is one of 'Element' or 'BackboneElement'.
function processAbstractElement(
  parentPath: string,
  name: string,
  value: Element | Element[],
  resourceElements: ElementDefinition[],
  typeElements: ElementDefinition[],
  type: Type,
  fetchStructureDefinition: (type: string) => StructureDefinition,
  getTypeElements: getTypeElementsFunction
): FhirProperty | undefined {
  return {
    name: name,
    type: type,
    value: Array.isArray(value)
      ? (value as Element[]).map((v) =>
          processAbstractElementValue(
            parentPath,
            resourceElements,
            typeElements,
            type,
            v,
            fetchStructureDefinition,
            getTypeElements
          )
        )
      : processAbstractElementValue(
          parentPath,
          resourceElements,
          typeElements,
          type,
          value,
          fetchStructureDefinition,
          getTypeElements
        )
  };
}

function processAbstractElementValue(
  parentPath: string,
  resourceElements: ElementDefinition[],
  typeElements: ElementDefinition[],
  type: Type,
  value: Element,
  fetchStructureDefinition: (type: string) => StructureDefinition,
  getTypeElements: getTypeElementsFunction
): FhirObject {
  const properties = typeElements
    .filter((element) => element.path.split('.').length == 2)
    .map((element) =>
      processElement(
        parentPath,
        element,
        resourceElements,
        typeElements,
        value,
        fetchStructureDefinition,
        getTypeElements
      )
    );
  return {
    type: type,
    properties: properties.filter((p) => p !== undefined).map((p) => p as FhirProperty),
    object: value
  };
}

function processTypedElement(
  element: ElementDefinition,
  resource: (Resource | Element) & { [key: string]: any },
  fetchStructureDefinition: (type: string) => StructureDefinition,
  getTypeElements: getTypeElementsFunction
): FhirProperty | undefined {
  const types = element.type;
  if (types === undefined) {
    return undefined;
  }
  const name = element.path.split('.')[1];

  // Handle polymorphic types
  if (name.endsWith('[x]')) {
    const rawName = name.substring(0, name.length - 3);

    // Find the first matching type
    let matchedType: ElementDefinitionType | undefined;
    let propertyName: string | undefined;

    for (const t of types) {
      const typeName = rawName + toTitleCase(t.code);
      if (resource[typeName] !== undefined) {
        matchedType = t;
        propertyName = typeName;
        break;
      }
    }

    if (!matchedType || !propertyName) {
      return undefined;
    }

    const type = mapType(matchedType);
    const value = resource[propertyName];

    if (value === undefined) {
      return undefined;
    }

    return {
      name: propertyName,
      humanName: rawName,
      type: type,
      value: isPrimitive(type)
        ? processPrimitiveValue(
            name,
            type,
            value,
            resource,
            fetchStructureDefinition,
            getTypeElements
          )
        : processNonPrimitiveValue(fetchStructureDefinition, getTypeElements, type, value)
    };
  }

  if (types.length == 0 || resource[name] === undefined) {
    return undefined;
  }

  const type = mapType(types[0]);
  const value = resource[name];

  return {
    name: name,
    type: type,
    value: isPrimitive(type)
      ? processPrimitiveValue(
          name,
          type,
          value,
          resource,
          fetchStructureDefinition,
          getTypeElements
        )
      : processNonPrimitiveValue(fetchStructureDefinition, getTypeElements, type, value)
  };
}

function processPrimitiveValue(
  name: string,
  type: Type,
  value: string | number | boolean,
  resource: (Resource | Element) & { [key: string]: any },
  fetchStructureDefinition: (type: string) => StructureDefinition,
  getTypeElements: getTypeElementsFunction
): FhirPrimitive[] | FhirPrimitive {
  if (Array.isArray(value)) {
    return value.map((v) =>
      processPrimitiveValue1(name, type, v, resource, fetchStructureDefinition, getTypeElements)
    );
  }

  return processPrimitiveValue1(
    name,
    type,
    value,
    resource,
    fetchStructureDefinition,
    getTypeElements
  );
}

function processPrimitiveValue1(
  name: string,
  type: Type,
  value: string | number | boolean,
  resource: (Resource | Element) & { [key: string]: any },
  fetchStructureDefinition: (type: string) => StructureDefinition,
  getTypeElements: getTypeElementsFunction
): FhirPrimitive {
  const siblingValue = resource['_' + name];
  const extension = siblingValue ? siblingValue['extension'] : undefined;
  return extension
    ? {
        type: type,
        value: value,
        extensions: processExtension(fetchStructureDefinition, getTypeElements, extension)
      }
    : {
        type: type,
        value: value
      };
}

function processExtension(
  fetchStructureDefinition: (type: string) => StructureDefinition,
  getTypeElements: getTypeElementsFunction,
  value: Extension[]
): FhirObject<Extension>[] {
  const structureDefinition = fetchStructureDefinition('Extension');

  return value.map(
    (v) =>
      calcPropertiesDeep(
        fetchStructureDefinition,
        getTypeElements,
        structureDefinition,
        v
      ) as FhirObject<Extension>
  );
}

function processNonPrimitiveValue(
  fetchStructureDefinition: (type: string) => StructureDefinition,
  getTypeElements: getTypeElementsFunction,
  type: Type,
  value: Element | Element[]
): FhirObject[] | FhirObject {
  const structureDefinition = fetchStructureDefinition(type.code);

  if (Array.isArray(value)) {
    return value.map((v) =>
      calcPropertiesDeep(fetchStructureDefinition, getTypeElements, structureDefinition, v)
    );
  }

  return calcPropertiesDeep(fetchStructureDefinition, getTypeElements, structureDefinition, value);
}

export function wrapPrimitiveExtensions(extensions: FhirObject<Extension>[]): FhirObject {
  return {
    type: { code: 'Element' },
    properties: [
      {
        name: 'extension',
        type: { code: 'Extension' },
        value: extensions
      }
    ],
    object: {
      extension: extensions.map((e) => e.object)
    }
  };
}
