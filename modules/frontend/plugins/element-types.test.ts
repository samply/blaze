import type { Bundle, ElementDefinition, StructureDefinition } from 'fhir/r4';
import { describe, expect, it } from 'vitest';
import { existsSync } from 'node:fs';
import { isAbsolute } from 'node:path';
import { generateElementTypes, profilesPath } from './element-types.js';

const fhirTypeUrl = 'http://hl7.org/fhir/StructureDefinition/structuredefinition-fhir-type';

function structureDefinition(
  type: string,
  elements: ElementDefinition[],
  other: Partial<StructureDefinition> = {}
): StructureDefinition {
  return {
    resourceType: 'StructureDefinition',
    url: `http://hl7.org/fhir/StructureDefinition/${type}`,
    name: type,
    status: 'active',
    kind: 'resource',
    abstract: false,
    type: type,
    derivation: 'specialization',
    snapshot: { element: [{ path: type }, ...elements] },
    ...other
  };
}

function bundle(...structureDefinitions: StructureDefinition[]): Bundle {
  return {
    resourceType: 'Bundle',
    type: 'collection',
    entry: structureDefinitions.map((resource) => ({ resource }))
  };
}

describe('generateElementTypes test', () => {
  it('contains the type of top-level elements', () => {
    const sd = structureDefinition('Patient', [
      { path: 'Patient.active', type: [{ code: 'boolean' }] },
      { path: 'Patient.birthDate', type: [{ code: 'date' }] },
      { path: 'Patient.name', type: [{ code: 'HumanName' }] }
    ]);

    expect(generateElementTypes(bundle(sd))).toStrictEqual({
      Patient: {
        active: { code: 'boolean' },
        birthDate: { code: 'date' },
        name: { code: 'HumanName' }
      }
    });
  });

  it('skips nested elements', () => {
    const sd = structureDefinition('Patient', [
      { path: 'Patient.contact', type: [{ code: 'BackboneElement' }] },
      { path: 'Patient.contact.name', type: [{ code: 'HumanName' }] }
    ]);

    expect(generateElementTypes(bundle(sd))).toStrictEqual({
      Patient: { contact: { code: 'BackboneElement' } }
    });
  });

  it('types elements defined by content reference as backbone elements', () => {
    const sd = structureDefinition('ClaimResponse', [
      { path: 'ClaimResponse.adjudication', contentReference: '#ClaimResponse.item.adjudication' }
    ]);

    expect(generateElementTypes(bundle(sd))).toStrictEqual({
      ClaimResponse: { adjudication: { code: 'BackboneElement' } }
    });
  });

  it('skips elements without type and content reference', () => {
    const sd = structureDefinition('Patient', [{ path: 'Patient.foo' }]);

    expect(generateElementTypes(bundle(sd))).toStrictEqual({ Patient: {} });
  });

  it('uses the FHIR type of elements with a FHIRPath system type', () => {
    const sd = structureDefinition('Patient', [
      {
        path: 'Patient.id',
        type: [
          {
            extension: [{ url: fhirTypeUrl, valueUrl: 'string' }],
            code: 'http://hl7.org/fhirpath/System.String'
          }
        ]
      }
    ]);

    expect(generateElementTypes(bundle(sd))).toStrictEqual({
      Patient: { id: { code: 'string' } }
    });
  });

  it('expands choice-typed elements into one entry per type', () => {
    const sd = structureDefinition('Observation', [
      {
        path: 'Observation.value[x]',
        type: [{ code: 'Quantity' }, { code: 'string' }, { code: 'dateTime' }]
      }
    ]);

    expect(generateElementTypes(bundle(sd))).toStrictEqual({
      Observation: {
        valueQuantity: { code: 'Quantity', choiceName: 'value' },
        valueString: { code: 'string', choiceName: 'value' },
        valueDateTime: { code: 'dateTime', choiceName: 'value' }
      }
    });
  });

  it('keeps the target profiles of canonicals only', () => {
    const sd = structureDefinition('Measure', [
      {
        path: 'Measure.library',
        type: [
          {
            code: 'canonical',
            targetProfile: ['http://hl7.org/fhir/StructureDefinition/Library']
          }
        ]
      },
      {
        path: 'Measure.subject[x]',
        type: [
          { code: 'CodeableConcept' },
          {
            code: 'Reference',
            targetProfile: ['http://hl7.org/fhir/StructureDefinition/Group']
          }
        ]
      }
    ]);

    expect(generateElementTypes(bundle(sd))).toStrictEqual({
      Measure: {
        library: {
          code: 'canonical',
          targetProfile: ['http://hl7.org/fhir/StructureDefinition/Library']
        },
        subjectCodeableConcept: { code: 'CodeableConcept', choiceName: 'subject' },
        subjectReference: { code: 'Reference', choiceName: 'subject' }
      }
    });
  });

  it('skips StructureDefinitions of non-resources, abstract resources and profiles', () => {
    const element = (type: string) => ({ path: `${type}.foo`, type: [{ code: 'string' }] });

    expect(
      generateElementTypes(
        bundle(
          structureDefinition('HumanName', [element('HumanName')], { kind: 'complex-type' }),
          structureDefinition('DomainResource', [element('DomainResource')], { abstract: true }),
          structureDefinition('Observation', [element('Observation')], {
            url: 'http://hl7.org/fhir/StructureDefinition/vitalsigns',
            derivation: 'constraint'
          }),
          { resourceType: 'Bundle', type: 'collection' } as unknown as StructureDefinition
        )
      )
    ).toStrictEqual({});
  });
});

describe('profilesPath test', () => {
  // independent of the working directory Vite or Vitest is started from
  it('is the absolute path of the FHIR definitions', () => {
    expect(isAbsolute(profilesPath)).toBe(true);
    expect(existsSync(profilesPath)).toBe(true);
  });
});
