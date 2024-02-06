import type {
	Resource,
	Element,
	StructureDefinition,
	ElementDefinition,
	ElementDefinitionType
} from '../fhir.js';
import { StructureDefinitionKind } from '../fhir.js';
import { toTitleCase } from '../util.js';
import { fetchStructureDefinition } from '../metadata.js';

export function isPrimitive(type: Type) {
	return type.code[0].toLowerCase() == type.code[0];
}

export interface Type {
	code: string;
	targetProfile?: string[];
}

export interface FhirType {
	type: Type;
}

/**
 * A structured representation of a Resource with ordered properties and type annotations.
 */
export interface FhirObject extends FhirType {
	properties: FhirProperty[];
	object: Element;
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

/**
 * Returns a Promise of a FhirObject of the given Resource.
 *
 * @param resource the Resource to use
 * @returns the FhirObject representation of the given Resource
 */
export async function fhirObject(
	resource: Resource,
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
	resource: Resource | Element
): Promise<FhirObject> {
	const properties = (
		await Promise.all(
			structureDefinition.snapshot.element
				.filter((element) => element.path.split('.').length == 2)
				.map((element) =>
					processElement(
						element,
						structureDefinition.snapshot.element,
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
			structureDefinition.kind == StructureDefinitionKind.resource
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

function dropFirstSegment(path: string): string {
	return path.split('.').slice(1).join('.');
}

async function processElement(
	element: ElementDefinition,
	elements: ElementDefinition[],
	resource: Resource | Element,
	fetchStructureDefinition: (type: string) => Promise<StructureDefinition>
): Promise<FhirProperty | undefined> {
	const type = onlyType(element);
	return type?.code == 'Element' || type?.code == 'BackboneElement'
		? processAbstractElement(
				element,
				elements
					.filter((e) => e.path.startsWith(element.path) && e.path != element.path)
					.map((e) => ({ ...e, path: dropFirstSegment(e.path) })),
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
	resource: Resource | Element,
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
	elements: ElementDefinition[],
	type: Type,
	resource: Resource | Element,
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
						processAbstractElementValue(elements, type, v, fetchStructureDefinition)
					)
				)
			: processAbstractElementValue(elements, type, value, fetchStructureDefinition))
	};
}

async function processAbstractElementValue(
	elements: ElementDefinition[],
	type: Type,
	value: Element,
	fetchStructureDefinition: (type: string) => Promise<StructureDefinition>
): Promise<FhirObject> {
	const properties = await Promise.all(
		elements
			.filter((element) => element.path.split('.').length == 2)
			.map((element) => processElement(element, elements, value, fetchStructureDefinition))
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
	resource: Resource | Element,
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
