import { describe, it, expect } from 'vitest';
import { render } from 'svelte/server';
import type { FhirResource } from 'fhir/r4';
import BreadcrumbEntryResource from '$lib/breadcrumb/resource.svelte';

function renderEntry(resource?: FhirResource): string {
  return render(BreadcrumbEntryResource, {
    props: { type: resource?.resourceType ?? 'Patient', id: '0', resource }
  }).body;
}

const patient: FhirResource = { resourceType: 'Patient', id: '0' };

describe('breadcrumb resource entry test', () => {
  it('shows the bare id if no resource is given', () => {
    expect(renderEntry()).toContain('>0<');
  });

  it('shows the bare id of a resource without title', () => {
    expect(renderEntry(patient)).toContain('>0<');
  });

  it('doesn\u2019t repeat the type of a resource without title', () => {
    expect(renderEntry(patient)).not.toContain('>Patient/0<');
  });

  it('shows the title and version of a code system', () => {
    const codeSystem: FhirResource = {
      resourceType: 'CodeSystem',
      id: '0',
      title: 'Foo',
      version: '1.0',
      status: 'active',
      content: 'complete'
    };
    expect(renderEntry(codeSystem)).toContain('>Foo v1.0<');
  });

  it('shows the title and version of a value set', () => {
    const valueSet: FhirResource = {
      resourceType: 'ValueSet',
      id: '0',
      title: 'Bar',
      version: '2.0',
      status: 'active'
    };
    expect(renderEntry(valueSet)).toContain('>Bar v2.0<');
  });

  it('shows the bare id of a code system without title', () => {
    const codeSystem: FhirResource = {
      resourceType: 'CodeSystem',
      id: '0',
      status: 'active',
      content: 'complete'
    };
    expect(renderEntry(codeSystem)).toContain('>0<');
  });

  it('links to the resource page if it isn\u2019t the last entry', () => {
    expect(renderEntry(patient)).toContain('href="/fhir/Patient/0"');
  });

  it('renders a span instead of a link if it is the last entry', () => {
    const body = render(BreadcrumbEntryResource, {
      props: { type: 'Patient', id: '0', resource: patient, last: true }
    }).body;
    expect(body).not.toContain('<a');
  });
});
