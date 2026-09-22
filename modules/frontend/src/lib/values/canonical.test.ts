import { describe, expect, it } from 'vitest';
import { targetType } from './canonical.js';

describe('targetType test', () => {
  it('returns undefined without target profile', () => {
    expect(targetType({ code: 'canonical' })).toBeUndefined();
  });

  it('returns undefined on empty target profile', () => {
    expect(targetType({ code: 'canonical', targetProfile: [] })).toBeUndefined();
  });

  it('returns the resource type of a core target profile', () => {
    expect(
      targetType({
        code: 'canonical',
        targetProfile: ['http://hl7.org/fhir/StructureDefinition/Library']
      })
    ).toBe('Library');
  });

  it('returns undefined on a non-core target profile', () => {
    expect(targetType({ code: 'canonical', targetProfile: ['http://example.com/Library'] })).toBe(
      undefined
    );
  });
});
