import { describe, it, expect } from 'vitest';
import type { Bundle, BundleEntry } from 'fhir/r4';
import { bundleSummaryMode, isSubsetted } from '$lib/resource/subsetted.js';

const subsettedTag = {
  system: 'http://terminology.hl7.org/CodeSystem/v3-ObservationValue',
  code: 'SUBSETTED'
};

function entry(...tag: { system: string; code: string }[]): BundleEntry {
  return { resource: { resourceType: 'Patient', meta: { tag: tag } } };
}

function deleteEntry(): BundleEntry {
  return { request: { method: 'DELETE', url: 'Patient/0' }, response: { status: '204' } };
}

function bundle(...entries: BundleEntry[]): Bundle {
  return { resourceType: 'Bundle', type: 'searchset', entry: entries };
}

describe('isSubsetted test', () => {
  it('returns false on an entry without resource', () => {
    expect(isSubsetted({})).toBe(false);
  });
  it('returns false on a resource without tags', () => {
    expect(isSubsetted(entry())).toBe(false);
  });
  it('returns false on a tag of another system', () => {
    expect(isSubsetted(entry({ system: 'other', code: 'SUBSETTED' }))).toBe(false);
  });
  it('returns true on the subsetted tag', () => {
    expect(isSubsetted(entry(subsettedTag))).toBe(true);
  });
});

describe('bundleSummaryMode test', () => {
  it('is unknown on a bundle without entries', () => {
    expect(bundleSummaryMode({ resourceType: 'Bundle', type: 'searchset' })).toBeUndefined();
    expect(bundleSummaryMode(bundle())).toBeUndefined();
  });
  it('is unknown on a bundle without resources', () => {
    expect(bundleSummaryMode(bundle(deleteEntry()))).toBeUndefined();
  });
  it('is full on a bundle with full resources', () => {
    expect(bundleSummaryMode(bundle(entry()))).toBe('full');
  });
  it('is summary on a bundle with a subsetted resource', () => {
    expect(bundleSummaryMode(bundle(entry(), entry(subsettedTag)))).toBe('summary');
  });
  it('ignores entries without resources', () => {
    expect(bundleSummaryMode(bundle(deleteEntry(), entry(subsettedTag)))).toBe('summary');
  });
});
