import { describe, expect, it } from 'vitest';
import { base, oldBase, url, matches } from './canonical.js';

describe('url test', () => {
  it('builds the current canonical', () => {
    expect(url('CodeSystem/JobType')).toBe('https://blaze-server.org/fhir/CodeSystem/JobType');
  });
});

describe('matches test', () => {
  it('matches the current canonical', () => {
    expect(matches(url('CodeSystem/JobType'), `${base}/CodeSystem/JobType`)).toBe(true);
  });
  it('matches the legacy canonical of the same path', () => {
    expect(matches(url('CodeSystem/JobType'), `${oldBase}/CodeSystem/JobType`)).toBe(true);
  });
  it('does not match a different path', () => {
    expect(matches(url('CodeSystem/JobType'), `${oldBase}/CodeSystem/Foo`)).toBe(false);
  });
  it('does not match a different base', () => {
    expect(matches(url('CodeSystem/JobType'), 'http://example.com/fhir/CodeSystem/JobType')).toBe(
      false
    );
  });
  it('does not match undefined', () => {
    expect(matches(url('CodeSystem/JobType'), undefined)).toBe(false);
  });
});
