import type { Bundle, ElementDefinition, StructureDefinition } from 'fhir/r5';
import { describe, expect, it } from 'vitest';
import { calcPropertiesDeep, type FhirObject, getTypeElements } from './resource-card.js';
import { readFileSync } from 'fs';

const structureDefinitionPatient = readStructureDefinition('Patient');
const structureDefinitionObservation = readStructureDefinition('Observation');
const structureDefinitionMedicationAdministration = readStructureDefinition(
  'MedicationAdministration'
);
const structureDefinitionCarePlan = readStructureDefinition('CarePlan');

function readBundle(name: string): Bundle {
  const data = readFileSync(name);
  return JSON.parse(data.toString());
}

function readStructureDefinitionFrom(file: string, name: string): StructureDefinition | undefined {
  const bundle = readBundle(`../fhir-structure/resources/blaze/fhir/profiles-${file}.json`);
  return bundle.entry?.find((e) => e.resource?.id === name)?.resource as StructureDefinition;
}

function readStructureDefinition(name: string): StructureDefinition {
  const structureDefinition = readStructureDefinitionFrom('types', name);
  if (structureDefinition === undefined) {
    const structureDefinition = readStructureDefinitionFrom('resources', name);
    return structureDefinition as StructureDefinition;
  } else {
    return structureDefinition;
  }
}

describe('calcPropertiesDeep test', () => {
  it('Patient.id', () => {
    expect(
      calcPropertiesDeep(
        readStructureDefinition,
        getTypeElements(new Map<string, ElementDefinition[]>()),
        structureDefinitionPatient,
        {
          resourceType: 'Patient',
          id: 'foo'
        }
      )
    ).toStrictEqual({
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
  it('Patient.meta.profile', () => {
    expect(
      calcPropertiesDeep(
        readStructureDefinition,
        getTypeElements(new Map<string, ElementDefinition[]>()),
        structureDefinitionPatient,
        {
          resourceType: 'Patient',
          meta: {
            profile: ['foo']
          }
        }
      )
    ).toStrictEqual({
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
  it('Patient active comes before gender', () => {
    expect(
      calcPropertiesDeep(
        readStructureDefinition,
        getTypeElements(new Map<string, ElementDefinition[]>()),
        structureDefinitionPatient,
        {
          resourceType: 'Patient',
          gender: 'male',
          active: false
        }
      )
    ).toStrictEqual({
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
  it('Patient.identifier', () => {
    expect(
      calcPropertiesDeep(
        readStructureDefinition,
        getTypeElements(new Map<string, ElementDefinition[]>()),
        structureDefinitionPatient,
        {
          resourceType: 'Patient',
          identifier: [
            {
              value: 'foo'
            }
          ]
        }
      )
    ).toStrictEqual({
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
  it('contained resource', () => {
    expect(
      calcPropertiesDeep(
        readStructureDefinition,
        getTypeElements(new Map<string, ElementDefinition[]>()),
        structureDefinitionPatient,
        {
          resourceType: 'Patient',
          contained: [
            {
              resourceType: 'Patient',
              gender: 'female'
            }
          ]
        }
      )
    ).toStrictEqual({
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
  it('polymorph attribute with complex type', () => {
    const result = calcPropertiesDeep(
      readStructureDefinition,
      getTypeElements(new Map<string, ElementDefinition[]>()),
      structureDefinitionMedicationAdministration,
      {
        resourceType: 'MedicationAdministration',
        status: 'unknown',
        medication: {
          concept: {
            text: 'text-131123'
          }
        },
        subject: {}
      }
    );
    expect(result).toHaveProperty('type', { code: 'MedicationAdministration' });
    expect(result).toHaveProperty('properties');
    expect(result.properties).toHaveLength(4);
    const thirdProperty = result.properties[2];
    expect(thirdProperty).toHaveProperty('name', 'medication');
    expect(thirdProperty).toHaveProperty('value');
    expect(thirdProperty.value).toHaveProperty('type', { code: 'CodeableReference' });
    expect(thirdProperty.value).toHaveProperty('properties');
    const thirdPropertyValue = thirdProperty.value as FhirObject;
    expect(thirdPropertyValue.properties).toHaveLength(1);
    expect(thirdPropertyValue.properties[0]).toHaveProperty('name', 'concept');
    expect(thirdPropertyValue.properties[0]).toHaveProperty('value', {
      type: { code: 'CodeableConcept' },
      object: { text: 'text-131123' },
      properties: [
        {
          name: 'text',
          type: { code: 'string' },
          value: {
            type: { code: 'string' },
            value: 'text-131123'
          }
        }
      ]
    });
  });
  it('backbone element', () => {
    const result = calcPropertiesDeep(
      readStructureDefinition,
      getTypeElements(new Map<string, ElementDefinition[]>()),
      structureDefinitionPatient,
      {
        resourceType: 'Patient',
        contact: [
          {
            gender: 'female'
          }
        ]
      }
    );
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
  it('multiple backbone elements', () => {
    const result = calcPropertiesDeep(
      readStructureDefinition,
      getTypeElements(new Map<string, ElementDefinition[]>()),
      structureDefinitionPatient,
      {
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
    );
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
  it('nested backbone elements', () => {
    const result = calcPropertiesDeep(
      readStructureDefinition,
      getTypeElements(new Map<string, ElementDefinition[]>()),
      structureDefinitionCarePlan,
      {
        resourceType: 'CarePlan',
        activity: [{}],
        status: 'unknown',
        intent: 'proposal',
        subject: {}
      }
    );
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
              properties: [],
              object: {}
            }
          ]
        }
      ],
      object: {
        resourceType: 'CarePlan',
        activity: [{}],
        status: 'unknown',
        intent: 'proposal',
        subject: {}
      }
    });
  });
  it('recursive backbone elements', () => {
    const result = calcPropertiesDeep(
      readStructureDefinition,
      getTypeElements(new Map<string, ElementDefinition[]>()),
      readStructureDefinition('Consent'),
      {
        resourceType: 'Consent',
        provision: [
          {
            provision: [{}]
          }
        ],
        category: [{}],
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
          value: [
            {
              type: { code: 'BackboneElement' },
              properties: [
                {
                  name: 'provision',
                  type: { code: 'BackboneElement' },
                  value: [
                    {
                      type: { code: 'BackboneElement' },
                      properties: [],
                      object: {}
                    }
                  ]
                }
              ],
              object: {
                provision: [{}]
              }
            }
          ]
        }
      ],
      object: {
        resourceType: 'Consent',
        provision: [
          {
            provision: [{}]
          }
        ],
        category: [{}],
        status: 'draft'
      }
    });
  });
  it('nested referenced backbone elements', () => {
    const result = calcPropertiesDeep(
      readStructureDefinition,
      getTypeElements(new Map<string, ElementDefinition[]>()),
      readStructureDefinition('ValueSet'),
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
  it('element', () => {
    const result = calcPropertiesDeep(
      readStructureDefinition,
      getTypeElements(new Map<string, ElementDefinition[]>()),
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
          humanName: 'effective',
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
  it('primitive extension', () => {
    expect(
      calcPropertiesDeep(
        readStructureDefinition,
        getTypeElements(new Map<string, ElementDefinition[]>()),
        structureDefinitionPatient,
        {
          resourceType: 'Patient',
          id: 'foo',
          gender: 'other',
          _gender: {
            extension: [
              {
                url: 'http://fhir.de/StructureDefinition/gender-amtlich-de',
                valueCoding: {
                  system: 'http://fhir.de/CodeSystem/gender-amtlich-de',
                  code: 'D',
                  display: 'divers'
                }
              }
            ]
          }
        }
      )
    ).toStrictEqual({
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
        },
        {
          name: 'gender',
          type: { code: 'code' },
          value: {
            type: { code: 'code' },
            value: 'other',
            extensions: [
              {
                type: { code: 'Extension' },
                properties: [
                  {
                    name: 'url',
                    type: { code: 'string' },
                    value: {
                      type: { code: 'string' },
                      value: 'http://fhir.de/StructureDefinition/gender-amtlich-de'
                    }
                  },
                  {
                    name: 'valueCoding',
                    humanName: 'value',
                    type: { code: 'Coding' },
                    value: {
                      type: { code: 'Coding' },
                      properties: [
                        {
                          name: 'system',
                          type: { code: 'uri' },
                          value: {
                            type: { code: 'uri' },
                            value: 'http://fhir.de/CodeSystem/gender-amtlich-de'
                          }
                        },
                        {
                          name: 'code',
                          type: { code: 'code' },
                          value: { type: { code: 'code' }, value: 'D' }
                        },
                        {
                          name: 'display',
                          type: { code: 'string' },
                          value: { type: { code: 'string' }, value: 'divers' }
                        }
                      ],
                      object: {
                        system: 'http://fhir.de/CodeSystem/gender-amtlich-de',
                        code: 'D',
                        display: 'divers'
                      }
                    }
                  }
                ],
                object: {
                  url: 'http://fhir.de/StructureDefinition/gender-amtlich-de',
                  valueCoding: {
                    system: 'http://fhir.de/CodeSystem/gender-amtlich-de',
                    code: 'D',
                    display: 'divers'
                  }
                }
              }
            ]
          }
        }
      ],
      object: {
        resourceType: 'Patient',
        id: 'foo',
        gender: 'other',
        _gender: {
          extension: [
            {
              url: 'http://fhir.de/StructureDefinition/gender-amtlich-de',
              valueCoding: {
                system: 'http://fhir.de/CodeSystem/gender-amtlich-de',
                code: 'D',
                display: 'divers'
              }
            }
          ]
        }
      }
    });
  });
});
