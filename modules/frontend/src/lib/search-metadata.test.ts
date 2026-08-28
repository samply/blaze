import { describe, it, expect } from 'vitest';
import type { CapabilityStatement } from 'fhir/r4';
import { emptySearchMetadata, searchMetadata } from '$lib/search-metadata.js';

function capabilityStatement(rest: CapabilityStatement['rest']): CapabilityStatement {
  return {
    resourceType: 'CapabilityStatement',
    status: 'active',
    date: '2026-01-01',
    kind: 'instance',
    fhirVersion: '4.0.1',
    format: ['application/fhir+json'],
    rest
  };
}

describe('emptySearchMetadata test', () => {
  it('offers nothing', () => {
    expect(emptySearchMetadata()).toEqual({
      searchParams: [],
      searchIncludes: [],
      searchRevIncludes: []
    });
  });

  it('returns a fresh instance on every call', () => {
    const metadata = emptySearchMetadata();
    metadata.searchIncludes.push('Patient:organization');

    expect(emptySearchMetadata().searchIncludes).toEqual([]);
  });
});

describe('searchMetadata test', () => {
  it('returns empty results on a CapabilityStatement without rest', () => {
    expect(searchMetadata(capabilityStatement(undefined), 'Patient')).toEqual({
      searchParams: [],
      searchIncludes: [],
      searchRevIncludes: []
    });
  });

  it('returns empty results on an unknown resource type', () => {
    const cs = capabilityStatement([
      {
        mode: 'server',
        resource: [{ type: 'Patient', searchInclude: ['Patient:organization'] }]
      }
    ]);

    expect(searchMetadata(cs, 'Observation')).toEqual({
      searchParams: [],
      searchIncludes: [],
      searchRevIncludes: []
    });
  });

  it('combines server-level and resource-level search params, sorted by name', () => {
    const cs = capabilityStatement([
      {
        mode: 'server',
        searchParam: [{ name: '_id', type: 'token' }],
        resource: [
          {
            type: 'Patient',
            searchParam: [
              { name: 'gender', type: 'token' },
              { name: 'birthdate', type: 'date' }
            ]
          }
        ]
      }
    ]);

    expect(searchMetadata(cs, 'Patient').searchParams).toEqual([
      { name: '_id', type: 'token' },
      { name: 'birthdate', type: 'date' },
      { name: 'gender', type: 'token' }
    ]);
  });

  it('returns the includes and reverse includes of the resource type', () => {
    const cs = capabilityStatement([
      {
        mode: 'server',
        resource: [
          {
            type: 'Patient',
            searchInclude: ['Patient:organization'],
            searchRevInclude: ['Observation:subject', 'Condition:subject']
          },
          { type: 'Observation', searchInclude: ['Observation:subject'] }
        ]
      }
    ]);

    expect(searchMetadata(cs, 'Patient')).toEqual({
      searchParams: [],
      searchIncludes: ['Patient:organization'],
      searchRevIncludes: ['Observation:subject', 'Condition:subject']
    });
  });

  it('only considers the first rest entry', () => {
    const cs = capabilityStatement([
      { mode: 'server', resource: [{ type: 'Patient', searchInclude: ['Patient:organization'] }] },
      { mode: 'client', resource: [{ type: 'Patient', searchInclude: ['Patient:link'] }] }
    ]);

    expect(searchMetadata(cs, 'Patient').searchIncludes).toEqual(['Patient:organization']);
  });
});
