import { describe, expect, it } from 'vitest';
import { dataAbsentReason, hasOtherExtensions } from './data-absent-reason.js';

const reason = {
  url: 'http://hl7.org/fhir/StructureDefinition/data-absent-reason',
  valueCode: 'asked-unknown'
};
const other = { url: 'http://example.com/ext', valueString: 'foo' };

describe('dataAbsentReason test', () => {
  it('returns the code of the data-absent-reason extension', () => {
    expect(dataAbsentReason({ extension: [other, reason] })).toBe('asked-unknown');
  });

  it('returns undefined without data-absent-reason extension', () => {
    expect(dataAbsentReason({ extension: [other] })).toBeUndefined();
  });

  it('returns undefined without extensions', () => {
    expect(dataAbsentReason({})).toBeUndefined();
    expect(dataAbsentReason(undefined)).toBeUndefined();
  });
});

describe('hasOtherExtensions test', () => {
  it('is true with an extension other than data-absent-reason', () => {
    expect(hasOtherExtensions({ extension: [reason, other] })).toBe(true);
  });

  it('is false with a data-absent-reason extension only', () => {
    expect(hasOtherExtensions({ extension: [reason] })).toBe(false);
  });

  it('is false without extensions', () => {
    expect(hasOtherExtensions({})).toBe(false);
    expect(hasOtherExtensions(undefined)).toBe(false);
  });
});
