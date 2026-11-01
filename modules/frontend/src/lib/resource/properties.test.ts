import type { Observation, Patient, Questionnaire } from 'fhir/r4';
import { describe, expect, it } from 'vitest';
import { properties } from './properties.js';

describe('properties test', () => {
  it('contains the top-level elements in JSON order', () => {
    const patient: Patient = {
      resourceType: 'Patient',
      id: '0',
      gender: 'female',
      active: true
    };

    expect(properties(patient)).toStrictEqual([
      { name: 'id', humanName: 'id', type: { code: 'string' }, values: ['0'], elements: [] },
      {
        name: 'gender',
        humanName: 'gender',
        type: { code: 'code' },
        values: ['female'],
        elements: []
      },
      {
        name: 'active',
        humanName: 'active',
        type: { code: 'boolean' },
        values: [true],
        elements: []
      }
    ]);
  });

  it('contains all values of arrays', () => {
    const patient: Patient = {
      resourceType: 'Patient',
      name: [{ family: 'foo' }, { family: 'bar' }]
    };

    expect(properties(patient)).toStrictEqual([
      {
        name: 'name',
        humanName: 'name',
        type: { code: 'HumanName' },
        values: [{ family: 'foo' }, { family: 'bar' }],
        elements: []
      }
    ]);
  });

  it('names choice-typed elements without type suffix', () => {
    const observation: Observation = {
      resourceType: 'Observation',
      status: 'final',
      code: { text: 'foo' },
      valueQuantity: { value: 1 }
    };

    expect(properties(observation)[2]).toStrictEqual({
      name: 'valueQuantity',
      humanName: 'value',
      type: { code: 'Quantity', choiceName: 'value' },
      values: [{ value: 1 }],
      elements: []
    });
  });

  it('contains the primitive extension element of a single value', () => {
    const extension = [{ url: 'foo', valueString: 'bar' }];
    const patient: Patient = {
      resourceType: 'Patient',
      birthDate: '2020',
      _birthDate: { extension }
    };

    expect(properties(patient)).toStrictEqual([
      {
        name: 'birthDate',
        humanName: 'birthDate',
        type: { code: 'date' },
        values: ['2020'],
        elements: [{ extension }]
      }
    ]);
  });

  it('contains the primitive extension elements of array values', () => {
    const extension = [{ url: 'foo', valueString: 'bar' }];
    const questionnaire: Questionnaire = {
      resourceType: 'Questionnaire',
      status: 'active',
      subjectType: ['Patient', 'Group'],
      _subjectType: [null, { extension }] as Questionnaire['_subjectType']
    };

    expect(properties(questionnaire)[1]).toStrictEqual({
      name: 'subjectType',
      humanName: 'subjectType',
      type: { code: 'code' },
      values: ['Patient', 'Group'],
      elements: [undefined, { extension }]
    });
  });

  it('contains a single primitive value with extensions only', () => {
    const extension = [
      {
        url: 'http://hl7.org/fhir/StructureDefinition/data-absent-reason',
        valueCode: 'unknown'
      }
    ];
    const patient: Patient = { resourceType: 'Patient', _birthDate: { extension } };

    expect(properties(patient)).toStrictEqual([
      {
        name: 'birthDate',
        humanName: 'birthDate',
        type: { code: 'date' },
        values: [undefined],
        elements: [{ extension }]
      }
    ]);
  });

  it('contains array primitive values with extensions only', () => {
    const extension = [{ url: 'foo', valueString: 'bar' }];
    const questionnaire: Questionnaire = {
      resourceType: 'Questionnaire',
      status: 'active',
      _subjectType: [{ extension }, { extension }]
    };

    expect(properties(questionnaire)[1]).toStrictEqual({
      name: 'subjectType',
      humanName: 'subjectType',
      type: { code: 'code' },
      values: [undefined, undefined],
      elements: [{ extension }, { extension }]
    });
  });

  it('names choice-typed primitive values with extensions only without type suffix', () => {
    const extension = [{ url: 'foo', valueString: 'bar' }];
    const observation = {
      resourceType: 'Observation',
      status: 'final',
      code: { text: 'foo' },
      _valueString: { extension }
    } as unknown as Observation;

    expect(properties(observation)[2]).toStrictEqual({
      name: 'valueString',
      humanName: 'value',
      type: { code: 'string', choiceName: 'value' },
      values: [undefined],
      elements: [{ extension }]
    });
  });

  it('skips extension elements of complex elements', () => {
    const patient = { resourceType: 'Patient', _name: { id: 'foo' } } as unknown as Patient;

    expect(properties(patient)).toStrictEqual([]);
  });

  it('skips unknown elements', () => {
    expect(properties({ resourceType: 'Patient', foo: 'bar' } as unknown as Patient)).toStrictEqual(
      []
    );
  });
});
