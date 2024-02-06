import type { StructureDefinition } from '../fhir.js';
import { describe, it, expect } from 'vitest';
import { error } from '@sveltejs/kit';
import { calcPropertiesDeep, type FhirObject } from './resource-card.js';
import { readFileSync } from 'fs';

const structureDefinitionPatient = await readStructureDefinition('Patient');
const structureDefinitionObservation = await readStructureDefinition('Observation');
const structureDefinitionMedicationAdministration = await readStructureDefinition(
	'MedicationAdministration'
);
const structureDefinitionCarePlan = await readStructureDefinition('CarePlan');

async function readStructureDefinition(name: string): Promise<StructureDefinition> {
	const data = readFileSync(`src/resource/test/${name}.json`);
	return Promise.resolve(JSON.parse(data.toString()));
}

describe('calcPropertiesDeep test', () => {
	it('Patient.id', async () => {
		await expect(
			calcPropertiesDeep(readStructureDefinition, structureDefinitionPatient, {
				resourceType: 'Patient',
				id: 'foo'
			})
		).resolves.toStrictEqual({
			type: { code: 'Patient' },
			properties: [
				{
					name: 'resourceType',
					type: { code: 'string' },
					value: { type: { code: 'string' }, value: 'Patient' }
				},
				{
					name: 'id',
					type: { code: 'string' },
					value: { type: { code: 'string' }, value: 'foo' }
				}
			],
			object: {
				resourceType: 'Patient',
				id: 'foo'
			}
		});
	});
	it('Patient.meta.profile', async () => {
		await expect(
			calcPropertiesDeep(readStructureDefinition, structureDefinitionPatient, {
				resourceType: 'Patient',
				meta: {
					profile: ['foo']
				}
			})
		).resolves.toStrictEqual({
			type: { code: 'Patient' },
			properties: [
				{
					name: 'resourceType',
					type: { code: 'string' },
					value: { type: { code: 'string' }, value: 'Patient' }
				},
				{
					name: 'meta',
					type: { code: 'Meta' },
					value: {
						type: { code: 'Meta' },
						properties: [
							{
								name: 'profile',
								type: {
									code: 'canonical',
									targetProfile: ['http://hl7.org/fhir/StructureDefinition/StructureDefinition']
								},
								value: [
									{
										type: {
											code: 'canonical',
											targetProfile: ['http://hl7.org/fhir/StructureDefinition/StructureDefinition']
										},
										value: 'foo'
									}
								]
							}
						],
						object: {
							profile: ['foo']
						}
					}
				}
			],
			object: {
				resourceType: 'Patient',
				meta: {
					profile: ['foo']
				}
			}
		});
	});
	it('Patient active comes before gender', async () => {
		await expect(
			calcPropertiesDeep(readStructureDefinition, structureDefinitionPatient, {
				resourceType: 'Patient',
				gender: 'male',
				active: false
			})
		).resolves.toStrictEqual({
			type: { code: 'Patient' },
			properties: [
				{
					name: 'resourceType',
					type: { code: 'string' },
					value: { type: { code: 'string' }, value: 'Patient' }
				},
				{
					name: 'active',
					type: { code: 'boolean' },
					value: { type: { code: 'boolean' }, value: false }
				},
				{
					name: 'gender',
					type: { code: 'code' },
					value: { type: { code: 'code' }, value: 'male' }
				}
			],
			object: {
				resourceType: 'Patient',
				gender: 'male',
				active: false
			}
		});
	});
	it('Patient.identifier', async () => {
		await expect(
			calcPropertiesDeep(readStructureDefinition, structureDefinitionPatient, {
				resourceType: 'Patient',
				identifier: [
					{
						value: 'foo'
					}
				]
			})
		).resolves.toStrictEqual({
			type: { code: 'Patient' },
			properties: [
				{
					name: 'resourceType',
					type: { code: 'string' },
					value: { type: { code: 'string' }, value: 'Patient' }
				},
				{
					name: 'identifier',
					type: { code: 'Identifier' },
					value: [
						{
							type: { code: 'Identifier' },
							properties: [
								{
									name: 'value',
									type: { code: 'string' },
									value: { type: { code: 'string' }, value: 'foo' }
								}
							],
							object: {
								value: 'foo'
							}
						}
					]
				}
			],
			object: {
				resourceType: 'Patient',
				identifier: [
					{
						value: 'foo'
					}
				]
			}
		});
	});
	it('contained resource', async () => {
		await expect(
			calcPropertiesDeep(readStructureDefinition, structureDefinitionPatient, {
				resourceType: 'Patient',
				contained: [
					{
						resourceType: 'Patient',
						gender: 'female'
					}
				]
			})
		).resolves.toStrictEqual({
			type: { code: 'Patient' },
			properties: [
				{
					name: 'resourceType',
					type: { code: 'string' },
					value: { type: { code: 'string' }, value: 'Patient' }
				},
				{
					name: 'contained',
					type: { code: 'Resource' },
					value: [
						{
							type: { code: 'Patient' },
							properties: [
								{
									name: 'resourceType',
									type: { code: 'string' },
									value: { type: { code: 'string' }, value: 'Patient' }
								},
								{
									name: 'gender',
									type: { code: 'code' },
									value: { type: { code: 'code' }, value: 'female' }
								}
							],
							object: {
								resourceType: 'Patient',
								gender: 'female'
							}
						}
					]
				}
			],
			object: {
				resourceType: 'Patient',
				contained: [
					{
						resourceType: 'Patient',
						gender: 'female'
					}
				]
			}
		});
	});
	it('polymorth attribute with complex type', async () => {
		const result = await calcPropertiesDeep(
			readStructureDefinition,
			structureDefinitionMedicationAdministration,
			{
				resourceType: 'MedicationAdministration',
				medicationCodeableConcept: {
					text: 'text-131123'
				}
			}
		);
		expect(result).toHaveProperty('type', { code: 'MedicationAdministration' });
		expect(result).toHaveProperty('properties');
		expect(result.properties).toHaveLength(2);
		const secondProperty = result.properties[1];
		expect(secondProperty).toHaveProperty('name', 'medicationCodeableConcept');
		expect(secondProperty).toHaveProperty('value');
		expect(secondProperty.value).toHaveProperty('type', { code: 'CodeableConcept' });
		expect(secondProperty.value).toHaveProperty('properties');
		const secondPropertyValue = secondProperty.value as FhirObject;
		expect(secondPropertyValue.properties).toHaveLength(1);
		expect(secondPropertyValue.properties[0]).toHaveProperty('name', 'text');
		expect(secondPropertyValue.properties[0]).toHaveProperty('value', {
			type: { code: 'string' },
			value: 'text-131123'
		});
	});
	it('backbone element', async () => {
		const result = await calcPropertiesDeep(readStructureDefinition, structureDefinitionPatient, {
			resourceType: 'Patient',
			contact: [
				{
					gender: 'female'
				}
			]
		});
		expect(result).toStrictEqual({
			type: { code: 'Patient' },
			properties: [
				{
					name: 'resourceType',
					type: { code: 'string' },
					value: { type: { code: 'string' }, value: 'Patient' }
				},
				{
					name: 'contact',
					type: { code: 'BackboneElement' },
					value: [
						{
							type: { code: 'BackboneElement' },
							properties: [
								{
									name: 'gender',
									type: { code: 'code' },
									value: { type: { code: 'code' }, value: 'female' }
								}
							],
							object: {
								gender: 'female'
							}
						}
					]
				}
			],
			object: {
				resourceType: 'Patient',
				contact: [
					{
						gender: 'female'
					}
				]
			}
		});
	});
	it('multiple backbone elements', async () => {
		const result = await calcPropertiesDeep(readStructureDefinition, structureDefinitionPatient, {
			resourceType: 'Patient',
			contact: [
				{
					gender: 'female'
				},
				{
					gender: 'male'
				}
			]
		});
		expect(result).toStrictEqual({
			type: { code: 'Patient' },
			properties: [
				{
					name: 'resourceType',
					type: { code: 'string' },
					value: { type: { code: 'string' }, value: 'Patient' }
				},
				{
					name: 'contact',
					type: { code: 'BackboneElement' },
					value: [
						{
							type: { code: 'BackboneElement' },
							properties: [
								{
									name: 'gender',
									type: { code: 'code' },
									value: { type: { code: 'code' }, value: 'female' }
								}
							],
							object: {
								gender: 'female'
							}
						},
						{
							type: { code: 'BackboneElement' },
							properties: [
								{
									name: 'gender',
									type: { code: 'code' },
									value: { type: { code: 'code' }, value: 'male' }
								}
							],
							object: {
								gender: 'male'
							}
						}
					]
				}
			],
			object: {
				resourceType: 'Patient',
				contact: [
					{
						gender: 'female'
					},
					{
						gender: 'male'
					}
				]
			}
		});
	});
	it('nested backbone elements', async () => {
		const result = await calcPropertiesDeep(readStructureDefinition, structureDefinitionCarePlan, {
			resourceType: 'CarePlan',
			activity: [
				{
					detail: {
						status: 'completed'
					}
				}
			]
		});
		expect(result).toStrictEqual({
			type: { code: 'CarePlan' },
			properties: [
				{
					name: 'resourceType',
					type: { code: 'string' },
					value: { type: { code: 'string' }, value: 'CarePlan' }
				},
				{
					name: 'activity',
					type: { code: 'BackboneElement' },
					value: [
						{
							type: { code: 'BackboneElement' },
							properties: [
								{
									name: 'detail',
									type: { code: 'BackboneElement' },
									value: {
										type: { code: 'BackboneElement' },
										properties: [
											{
												name: 'status',
												type: { code: 'code' },
												value: { type: { code: 'code' }, value: 'completed' }
											}
										],
										object: {
											status: 'completed'
										}
									}
								}
							],
							object: {
								detail: {
									status: 'completed'
								}
							}
						}
					]
				}
			],
			object: {
				resourceType: 'CarePlan',
				activity: [
					{
						detail: {
							status: 'completed'
						}
					}
				]
			}
		});
	});
	it('element', async () => {
		const result = await calcPropertiesDeep(
			readStructureDefinition,
			structureDefinitionObservation,
			{
				resourceType: 'Observation',
				effectiveTiming: {
					repeat: {
						frequency: 1
					}
				}
			}
		);
		expect(result).toStrictEqual({
			type: { code: 'Observation' },
			properties: [
				{
					name: 'resourceType',
					type: { code: 'string' },
					value: { type: { code: 'string' }, value: 'Observation' }
				},
				{
					name: 'effectiveTiming',
					type: { code: 'Timing' },
					value: {
						type: { code: 'Timing' },
						properties: [
							{
								name: 'repeat',
								type: { code: 'Element' },
								value: {
									type: { code: 'Element' },
									properties: [
										{
											name: 'frequency',
											type: { code: 'positiveInt' },
											value: { type: { code: 'positiveInt' }, value: 1 }
										}
									],
									object: {
										frequency: 1
									}
								}
							}
						],
						object: {
							repeat: {
								frequency: 1
							}
						}
					}
				}
			],
			object: {
				resourceType: 'Observation',
				effectiveTiming: {
					repeat: {
						frequency: 1
					}
				}
			}
		});
	});
	it('with error during StructureDefinition fetch', async () => {
		await expect(() =>
			calcPropertiesDeep(
				() => {
					error(404);
				},
				structureDefinitionPatient,
				{
					resourceType: 'Patient',
					identifier: [{}]
				}
			)
		).rejects.toThrowError();
	});
});
