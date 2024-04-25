/* eslint-disable @typescript-eslint/no-explicit-any */

import type {
	Resource,
	Element,
	Bundle,
	BundleEntry,
	StructureDefinition,
	ElementDefinition,
	ElementDefinitionType,
	Address,
	Attachment,
	Identifier,
	FhirResource,
	Money,
	Period,
	Quantity,
	Coding,
	CodeableConcept,
	Meta,
	HumanName
} from 'fhir/r4';
import { toTitleCase } from '../util.js';
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
export interface FhirObject<ObjectType = FhirComplexType> extends FhirType {
	properties: FhirProperty[];
	object: ObjectType;
}

/**
 * A named property. The name includes the type for polymorph properties.
 */
export interface FhirProperty {
	name: string;
	type: Type;
	value: FhirObject[] | FhirObject | FhirPrimitive[] | FhirPrimitive;
}

export interface FhirPrimitive extends FhirType {
	value: string | number | boolean;
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
	const structureDefinition = await fetchStructureDefinition(resource.resourceType, fetch);
	return calcPropertiesDeep(
		(type) => fetchStructureDefinition(type, fetch),
		structureDefinition,
		resource
	);
}

function onlyType(element: ElementDefinition): Type | undefined {
	return element?.type?.length == 1 ? element.type[0] : undefined;
}

export async function calcPropertiesDeep(
	fetchStructureDefinition: (type: string) => Promise<StructureDefinition>,
	structureDefinition: StructureDefinition,
	resource: FhirResource | Element
): Promise<FhirObject> {
	const properties = (
		await Promise.all(
			(structureDefinition.snapshot?.element || [])
				.filter((element) => element.path.split('.').length == 2)
				.map((element) =>
					processElement(
						element,
						structureDefinition.snapshot?.element || [],
						structureDefinition.snapshot?.element || [],
						resource,
						fetchStructureDefinition
					)
				)
		)
	)
		.filter((p) => p !== undefined)
		// eslint-disable-next-line
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

async function processElement(
	element: ElementDefinition,
	resourceElements: ElementDefinition[],
	typeElements: ElementDefinition[],
	resource: Resource | Element,
	fetchStructureDefinition: (type: string) => Promise<StructureDefinition>
): Promise<FhirProperty | undefined> {
	if (element.contentReference !== undefined) {
		const id = element.contentReference.substring(1);
		const referencedElement = resourceElements.find((e) => e.id === id);
		return referencedElement === undefined
			? undefined
			: processAbstractElement(
					{ ...referencedElement, path: element.path },
					resourceElements,
					resourceElements
						.filter(
							(e) => e.path.startsWith(referencedElement.path) && e.path != referencedElement.path
						)
						.map((e) => ({
							...e,
							path: dropSegments(e.path, referencedElement.path.split('.').length - 1)
						})),
					{ code: 'BackboneElement' },
					resource,
					fetchStructureDefinition
				);
	}

	const type = onlyType(element);
	return type?.code == 'Element' || type?.code == 'BackboneElement'
		? processAbstractElement(
				element,
				resourceElements,
				typeElements
					.filter((e) => e.path.startsWith(element.path) && e.path != element.path)
					.map((e) => ({ ...e, path: dropSegments(e.path, 1) })),
				type,
				resource,
				fetchStructureDefinition
			)
		: type?.code == 'Resource'
			? processResourceElement(element, resource, fetchStructureDefinition)
			: processTypedElement(element, resource, fetchStructureDefinition);
}

function mapType(type: ElementDefinitionType): Type {
	const t = {
		code: type.code == 'http://hl7.org/fhirpath/System.String' ? 'string' : type.code
	};
	return type.targetProfile !== undefined ? { ...t, targetProfile: type.targetProfile } : t;
}

async function processResourceElement(
	element: ElementDefinition,
	resource: (Resource | Element) & { [key: string]: any },
	fetchStructureDefinition: (type: string) => Promise<StructureDefinition>
): Promise<FhirProperty | undefined> {
	const name = element.path.split('.')[1];
	const value = resource[name];

	if (value === undefined) {
		return undefined;
	}

	return {
		name: name,
		type: { code: 'Resource' },
		value: await (Array.isArray(value)
			? Promise.all(
					(value as Resource[]).map((v) => processResourceValue(v, fetchStructureDefinition))
				)
			: processResourceValue(value, fetchStructureDefinition))
	};
}

async function processResourceValue(
	resource: Resource,
	fetchStructureDefinition: (type: string) => Promise<StructureDefinition>
): Promise<FhirObject> {
	const structureDefinition = await fetchStructureDefinition(resource.resourceType);
	return calcPropertiesDeep(fetchStructureDefinition, structureDefinition, resource);
}

// Processes one of the abstract elements. Type is one of 'Element' or 'BackboneElement'.
async function processAbstractElement(
	element: ElementDefinition,
	resourceElements: ElementDefinition[],
	typeElements: ElementDefinition[],
	type: Type,
	resource: (Resource | Element) & { [key: string]: any },
	fetchStructureDefinition: (type: string) => Promise<StructureDefinition>
): Promise<FhirProperty | undefined> {
	const path = element.path;
	const name = path.split('.')[1];
	const value = resource[name];

	if (value === undefined) {
		return undefined;
	}

	return {
		name: name,
		type: type,
		value: await (Array.isArray(value)
			? Promise.all(
					(value as Element[]).map((v) =>
						processAbstractElementValue(
							resourceElements,
							typeElements,
							type,
							v,
							fetchStructureDefinition
						)
					)
				)
			: processAbstractElementValue(
					resourceElements,
					typeElements,
					type,
					value,
					fetchStructureDefinition
				))
	};
}

async function processAbstractElementValue(
	resourceElements: ElementDefinition[],
	typeElements: ElementDefinition[],
	type: Type,
	value: Element,
	fetchStructureDefinition: (type: string) => Promise<StructureDefinition>
): Promise<FhirObject> {
	const properties = await Promise.all(
		typeElements
			.filter((element) => element.path.split('.').length == 2)
			.map((element) =>
				processElement(element, resourceElements, typeElements, value, fetchStructureDefinition)
			)
	);
	return {
		type: type,
		properties: properties
			.filter((p) => p !== undefined)
			// eslint-disable-next-line
			.map((p) => p as FhirProperty),
		object: value
	};
}

async function processTypedElement(
	element: ElementDefinition,
	resource: (Resource | Element) & { [key: string]: any },
	fetchStructureDefinition: (type: string) => Promise<StructureDefinition>
): Promise<FhirProperty | undefined> {
	const types = element.type;
	if (types === undefined) {
		return undefined;
	}
	const name = element.path.split('.')[1];
	if (name.endsWith('[x]')) {
		const rawName = name.substring(0, name.length - 3);
		const filteredTypes = types.filter(
			(t) => resource[rawName + toTitleCase(t.code)] !== undefined
		);

		if (filteredTypes.length == 0) {
			return undefined;
		}

		const propertyName = rawName + toTitleCase(filteredTypes[0].code);
		const type = mapType(filteredTypes[0]);
		const value = resource[propertyName];

		if (value === undefined) {
			return undefined;
		}

		return {
			name: propertyName,
			type: type,
			value: isPrimitive(type)
				? processPrimitiveValue(type, value)
				: await processNonPrimitiveValue(fetchStructureDefinition, type, value)
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
			? processPrimitiveValue(type, value)
			: await processNonPrimitiveValue(fetchStructureDefinition, type, value)
	};
}

function processPrimitiveValue(
	type: Type,
	value: string | number | boolean
): FhirPrimitive[] | FhirPrimitive {
	return Array.isArray(value)
		? value.map((v) => ({
				type: type,
				value: v
			}))
		: {
				type: type,
				value: value
			};
}

async function processNonPrimitiveValue(
	fetchStructureDefinition: (type: string) => Promise<StructureDefinition>,
	type: Type,
	value: Element
): Promise<FhirObject[] | FhirObject> {
	const structureDefinition = await fetchStructureDefinition(type.code);

	if (Array.isArray(value)) {
		return Promise.all(
			value.map((v) => calcPropertiesDeep(fetchStructureDefinition, structureDefinition, v))
		);
	}

	return calcPropertiesDeep(fetchStructureDefinition, structureDefinition, value);
}
