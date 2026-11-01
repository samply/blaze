import { describe, expect, it } from 'vitest';
import { elementType, isPrimitive } from './element-types.js';

describe('elementType test', () => {
  it('returns the type of a top-level element', () => {
    expect(elementType('Patient', 'birthDate')).toStrictEqual({ code: 'date' });
  });

  it('returns the type of a choice-typed element', () => {
    expect(elementType('Observation', 'valueQuantity')).toStrictEqual({
      code: 'Quantity',
      choiceName: 'value'
    });
  });

  it('returns the target profiles of a canonical', () => {
    expect(elementType('Measure', 'library')).toStrictEqual({
      code: 'canonical',
      targetProfile: ['http://hl7.org/fhir/StructureDefinition/Library']
    });
  });

  it('returns the type of an element defined by content reference', () => {
    expect(elementType('ClaimResponse', 'adjudication')).toStrictEqual({
      code: 'BackboneElement'
    });
  });

  it('returns undefined for unknown elements', () => {
    expect(elementType('Patient', 'foo')).toBeUndefined();
    expect(elementType('Patient', 'resourceType')).toBeUndefined();
    expect(elementType('Patient', 'constructor')).toBeUndefined();
  });

  it('returns undefined for unknown resource types', () => {
    expect(elementType('Foo', 'id')).toBeUndefined();
    expect(elementType('toString', 'id')).toBeUndefined();
  });
});

describe('isPrimitive test', () => {
  it('is true for primitive types', () => {
    expect(isPrimitive({ code: 'date' })).toBe(true);
  });

  it('is false for complex types', () => {
    expect(isPrimitive({ code: 'HumanName' })).toBe(false);
  });
});
