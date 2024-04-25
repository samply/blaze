import type { Bundle, StructureDefinition } from 'fhir/r4';
import { describe, expect, it } from 'vitest';
import { error } from '@sveltejs/kit';
import { calcPropertiesDeep, type FhirObject } from './resource-card.js';
import { readFileSync } from 'fs';

const structureDefinitionPatient = await readStructureDefinition('Patient');
const structureDefinitionObservation = await readStructureDefinition('Observation');
const structureDefinitionMedicationAdministration = await readStructureDefinition(
	'MedicationAdministration'
);
const structureDefinitionCarePlan = await readStructureDefinition('CarePlan');

function readBundle(name: string): Bundle {
	const data = readFileSync(name);
	return JSON.parse(data.toString());
}

function readStructureDefinitionFrom(file: string, name: string): StructureDefinition | undefined {
	const bundle = readBundle(`../fhir-structure/resources/blaze/fhir/r4/profiles-${file}.json`);
	return bundle.entry?.find((e) => e.resource?.id === name)?.resource as StructureDefinition;
}

async function readStructureDefinition(name: string): Promise<StructureDefinition> {
	const structureDefinition = readStructureDefinitionFrom('types', name);
	if (structureDefinition === undefined) {
		const structureDefinition = readStructureDefinitionFrom('resources', name);
		return structureDefinition === undefined
			? Promise.reject(`StructureDefinition ${name} not found`)
			: Promise.resolve(structureDefinition);
	} else {
		return Promise.resolve(structureDefinition);
	}
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
	it('polymorph attribute with complex type', async () => {
		const result = await calcPropertiesDeep(
			readStructureDefinition,
			structureDefinitionMedicationAdministration,
			{
				resourceType: 'MedicationAdministration',
				status: 'unknown',
				medicationCodeableConcept: {
					text: 'text-131123'
				},
				subject: {}
			}
		);
		expect(result).toHaveProperty('type', { code: 'MedicationAdministration' });
		expect(result).toHaveProperty('properties');
		expect(result.properties).toHaveLength(4);
		const thirdProperty = result.properties[2];
		expect(thirdProperty).toHaveProperty('name', 'medicationCodeableConcept');
		expect(thirdProperty).toHaveProperty('value');
		expect(thirdProperty.value).toHaveProperty('type', { code: 'CodeableConcept' });
		expect(thirdProperty.value).toHaveProperty('properties');
		const thirdPropertyValue = thirdProperty.value as FhirObject;
		expect(thirdPropertyValue.properties).toHaveLength(1);
		expect(thirdPropertyValue.properties[0]).toHaveProperty('name', 'text');
		expect(thirdPropertyValue.properties[0]).toHaveProperty('value', {
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
			],
			status: 'unknown',
			intent: 'proposal',
			subject: {}
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
					name: 'status',
					type: { code: 'code' },
					value: { type: { code: 'code' }, value: 'unknown' }
				},
				{
					name: 'intent',
					type: { code: 'code' },
					value: { type: { code: 'code' }, value: 'proposal' }
				},
				{
					name: 'subject',
					type: {
						code: 'Reference',
						targetProfile: [
							'http://hl7.org/fhir/StructureDefinition/Patient',
							'http://hl7.org/fhir/StructureDefinition/Group'
						]
					},
					value: { type: { code: 'Reference' }, properties: [], object: {} }
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
				],
				status: 'unknown',
				intent: 'proposal',
				subject: {}
			}
		});
	});
	it('recursive backbone elements', async () => {
		const result = await calcPropertiesDeep(
			readStructureDefinition,
			await readStructureDefinition('Consent'),
			{
				resourceType: 'Consent',
				provision: {
					type: 'deny',
					provision: [
						{
							type: 'permit'
						}
					]
				},
				category: [{}],
				scope: {},
				status: 'draft'
			}
		);
		expect(result).toStrictEqual({
			type: { code: 'Consent' },
			properties: [
				{
					name: 'resourceType',
					type: { code: 'string' },
					value: { type: { code: 'string' }, value: 'Consent' }
				},
				{
					name: 'status',
					type: { code: 'code' },
					value: { type: { code: 'code' }, value: 'draft' }
				},
				{
					name: 'scope',
					type: { code: 'CodeableConcept' },
					value: {
						type: { code: 'CodeableConcept' },
						properties: [],
						object: {}
					}
				},
				{
					name: 'category',
					type: { code: 'CodeableConcept' },
					value: [
						{
							type: { code: 'CodeableConcept' },
							properties: [],
							object: {}
						}
					]
				},
				{
					name: 'provision',
					type: { code: 'BackboneElement' },
					value: {
						type: { code: 'BackboneElement' },
						properties: [
							{
								name: 'type',
								type: { code: 'code' },
								value: { type: { code: 'code' }, value: 'deny' }
							},
							{
								name: 'provision',
								type: { code: 'BackboneElement' },
								value: [
									{
										type: { code: 'BackboneElement' },
										properties: [
											{
												name: 'type',
												type: { code: 'code' },
												value: { type: { code: 'code' }, value: 'permit' }
											}
										],
										object: {
											type: 'permit'
										}
									}
								]
							}
						],
						object: {
							type: 'deny',
							provision: [
								{
									type: 'permit'
								}
							]
						}
					}
				}
			],
			object: {
				resourceType: 'Consent',
				provision: {
					type: 'deny',
					provision: [
						{
							type: 'permit'
						}
					]
				},
				category: [{}],
				scope: {},
				status: 'draft'
			}
		});
	});
	it('nested referenced backbone elements', async () => {
		const result = await calcPropertiesDeep(
			readStructureDefinition,
			await readStructureDefinition('ValueSet'),
			{
				resourceType: 'ValueSet',
				compose: {
					include: [
						{
							system: 'system-1'
						}
					],
					exclude: [
						{
							system: 'system-2'
						}
					]
				},
				status: 'draft'
			}
		);
		expect(result).toStrictEqual({
			type: { code: 'ValueSet' },
			properties: [
				{
					name: 'resourceType',
					type: { code: 'string' },
					value: { type: { code: 'string' }, value: 'ValueSet' }
				},
				{
					name: 'status',
					type: { code: 'code' },
					value: { type: { code: 'code' }, value: 'draft' }
				},
				{
					name: 'compose',
					type: { code: 'BackboneElement' },
					value: {
						type: { code: 'BackboneElement' },
						properties: [
							{
								name: 'include',
								type: { code: 'BackboneElement' },
								value: [
									{
										type: { code: 'BackboneElement' },
										properties: [
											{
												name: 'system',
												type: { code: 'uri' },
												value: { type: { code: 'uri' }, value: 'system-1' }
											}
										],
										object: {
											system: 'system-1'
										}
									}
								]
							},
							{
								name: 'exclude',
								type: { code: 'BackboneElement' },
								value: [
									{
										type: { code: 'BackboneElement' },
										properties: [
											{
												name: 'system',
												type: { code: 'uri' },
												value: { type: { code: 'uri' }, value: 'system-2' }
											}
										],
										object: {
											system: 'system-2'
										}
									}
								]
							}
						],
						object: {
							include: [
								{
									system: 'system-1'
								}
							],
							exclude: [
								{
									system: 'system-2'
								}
							]
						}
					}
				}
			],
			object: {
				resourceType: 'ValueSet',
				compose: {
					include: [
						{
							system: 'system-1'
						}
					],
					exclude: [
						{
							system: 'system-2'
						}
					]
				},
				status: 'draft'
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
				},
				code: {},
				status: 'final'
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
					name: 'status',
					type: { code: 'code' },
					value: { type: { code: 'code' }, value: 'final' }
				},
				{
					name: 'code',
					type: { code: 'CodeableConcept' },
					value: { type: { code: 'CodeableConcept' }, object: {}, properties: [] }
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
				},
				code: {},
				status: 'final'
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
