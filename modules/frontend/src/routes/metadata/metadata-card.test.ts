import { describe, it, expect, vi } from 'vitest';
import { render } from 'svelte/server';
import type { CapabilityStatement } from 'fhir/r4';

vi.mock('$app/state', () => ({
  page: { url: new URL('http://localhost/fhir/metadata') }
}));

const { default: MetadataCard } = await import('./metadata-card.svelte');

const capabilityStatement: CapabilityStatement = {
  resourceType: 'CapabilityStatement',
  status: 'active',
  date: '2026-10-24',
  kind: 'instance',
  fhirVersion: '4.0.1',
  format: ['json'],
  software: { name: 'Blaze', version: '1.13.0' }
};

describe('metadata card test', () => {
  // keeps the label and the date on the same line on narrow screens
  it('joins the last updated label and its date with a non-breaking space', () => {
    const { body } = render(MetadataCard, {
      props: {
        capabilityStatement,
        capabilityStatementObject: {
          type: { code: 'CapabilityStatement' },
          object: capabilityStatement,
          properties: []
        }
      }
    });

    const nbsp = String.fromCharCode(0xa0);
    expect(body).toContain(`Last Updated${nbsp}<time`);
  });
});
