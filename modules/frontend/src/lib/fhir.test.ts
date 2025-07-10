import type { Bundle } from 'fhir/r4';

import { describe, expect, it } from 'vitest';
import { bundleLink } from './fhir.js';

describe('bundleLink test', () => {
  it('missing link property', () => {
    const bundle: Bundle = { resourceType: 'Bundle', type: 'searchset' };
    expect(bundleLink(bundle, 'foo')).toBeUndefined();
  });
  it('empty link array', () => {
    const bundle: Bundle = {
      resourceType: 'Bundle',
      type: 'searchset',
      link: []
    };
    expect(bundleLink(bundle, 'foo')).toBeUndefined();
  });
  it('non-matching link relation', () => {
    const bundle: Bundle = {
      resourceType: 'Bundle',
      type: 'searchset',
      link: [{ relation: 'foo', url: 'bar' }]
    };
    expect(bundleLink(bundle, 'bar')).toBeUndefined();
  });
  it('matching link relation', () => {
    const bundle: Bundle = {
      resourceType: 'Bundle',
      type: 'searchset',
      link: [{ relation: 'foo', url: 'bar' }]
    };
    expect(bundleLink(bundle, 'foo')).toStrictEqual({ relation: 'foo', url: 'bar' });
  });
});
