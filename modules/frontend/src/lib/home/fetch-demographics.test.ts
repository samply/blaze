import { describe, it, expect, vi } from 'vitest';
import type { MeasureReport, Parameters } from 'fhir/r4';
import { demographicsMeasureUrl } from './demographics.js';
import { fetchDemographics } from './fetch-demographics.js';

const report: MeasureReport = {
  resourceType: 'MeasureReport',
  status: 'complete',
  type: 'summary',
  measure: demographicsMeasureUrl,
  period: { start: '2026', end: '2026' },
  group: [{ population: [{ count: 42 }] }]
};

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/fhir+json' }
  });
}

describe('fetchDemographics test', () => {
  it('posts the inline measure to $evaluate-measure', async () => {
    const fetch = vi.fn(async () => jsonResponse(200, report));

    const demographics = await fetchDemographics(fetch, 2026);

    expect(demographics.total).toBe(42);

    const [url, init] = fetch.mock.calls[0] as unknown as [string, RequestInit];
    expect(url).toBe('/fhir/Measure/$evaluate-measure');
    expect(init.method).toBe('POST');
    expect(init.headers).toEqual({
      Accept: 'application/fhir+json',
      'Content-Type': 'application/fhir+json'
    });
    const body = JSON.parse(init.body as string) as Parameters;
    expect(body.parameter?.map((p) => p.name)).toEqual(['periodStart', 'periodEnd', 'measure']);
  });

  it('fails with the diagnostics of the OperationOutcome', async () => {
    const fetch = vi.fn(async () =>
      jsonResponse(403, {
        resourceType: 'OperationOutcome',
        issue: [{ severity: 'error', code: 'forbidden', diagnostics: 'Insufficient scope.' }]
      })
    );

    await expect(fetchDemographics(fetch, 2026)).rejects.toMatchObject({
      status: 403,
      body: { message: 'Insufficient scope.' }
    });
  });

  it('fails with a generic message without OperationOutcome', async () => {
    const fetch = vi.fn(async () => new Response('Bad Gateway', { status: 502 }));

    await expect(fetchDemographics(fetch, 2026)).rejects.toMatchObject({
      status: 502,
      body: { message: 'Error while evaluating the patient demographics.' }
    });
  });
});
