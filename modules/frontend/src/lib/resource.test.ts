import { describe, it, expect } from 'vitest';
import type { FhirResource } from 'fhir/r4';
import { title, versionedTitle } from '$lib/resource.js';

const patient: FhirResource = { resourceType: 'Patient', id: '0' };

const codeSystem: FhirResource = {
  resourceType: 'CodeSystem',
  id: '0',
  title: 'Foo',
  version: '1.0',
  status: 'active',
  content: 'complete'
};

const valueSet: FhirResource = {
  resourceType: 'ValueSet',
  id: '0',
  title: 'Bar',
  version: '2.0',
  status: 'active'
};

describe('title test', () => {
  it('uses the type and the id of a resource without title', () => {
    expect(title(patient)).toBe('Patient/0');
  });

  it('uses the title and version of a code system', () => {
    expect(title(codeSystem)).toBe('Foo v1.0');
  });

  it('uses the title and version of a value set', () => {
    expect(title(valueSet)).toBe('Bar v2.0');
  });

  it('uses the type and the id of a code system without version', () => {
    expect(title({ ...codeSystem, version: undefined })).toBe('CodeSystem/0');
  });

  it('uses the type and the id of a value set without title', () => {
    expect(title({ ...valueSet, title: undefined })).toBe('ValueSet/0');
  });
});

describe('versionedTitle test', () => {
  it('is undefined for a resource without title', () => {
    expect(versionedTitle(patient)).toBeUndefined();
  });

  it('combines the title and version of a code system', () => {
    expect(versionedTitle(codeSystem)).toBe('Foo v1.0');
  });

  it('combines the title and version of a value set', () => {
    expect(versionedTitle(valueSet)).toBe('Bar v2.0');
  });

  it('is undefined for a code system without title', () => {
    expect(versionedTitle({ ...codeSystem, title: undefined })).toBeUndefined();
  });

  it('is undefined for a value set without version', () => {
    expect(versionedTitle({ ...valueSet, version: undefined })).toBeUndefined();
  });
});
