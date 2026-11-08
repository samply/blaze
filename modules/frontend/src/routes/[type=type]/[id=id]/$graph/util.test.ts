import { describe, it, expect } from 'vitest';
import { appError, graphDefinitionsQuery, graphUrl, startTypes } from './util.js';

const params = { type: 'Patient', id: '0' };

function outcomeResponse(status: number, diagnostics?: string): Response {
  return new Response(
    JSON.stringify({
      resourceType: 'OperationOutcome',
      issue: [{ severity: 'error', code: 'invalid', diagnostics }]
    }),
    { status, headers: { 'Content-Type': 'application/fhir+json' } }
  );
}

describe('appError test', () => {
  it('uses the diagnostics of the OperationOutcome on 400', async () => {
    const msg =
      "The type `Patient` of the resource `Patient/0` doesn't match the type `Encounter` of the start node `encounter`.";
    expect(await appError(params, outcomeResponse(400, msg))).toStrictEqual({
      short: 'Bad Request',
      message: msg
    });
  });
  it('falls back to a generic message on 400 without diagnostics', async () => {
    expect(await appError(params, outcomeResponse(400))).toStrictEqual({
      short: 'Bad Request',
      message: 'Please check the selected GraphDefinition.'
    });
  });
  it('falls back to a generic message on 400 without OperationOutcome', async () => {
    expect(await appError(params, new Response('', { status: 400 }))).toStrictEqual({
      short: 'Bad Request',
      message: 'Please check the selected GraphDefinition.'
    });
  });
  it('reports a missing resource on 404', async () => {
    expect(await appError(params, outcomeResponse(404))).toStrictEqual({
      short: 'Not Found',
      message: 'The Patient with ID 0 was not found.'
    });
  });
  it('reports a deleted resource on 410', async () => {
    expect(await appError(params, outcomeResponse(410))).toStrictEqual({
      short: 'Gone',
      message: 'The Patient with ID 0 was deleted. Please look into the history.'
    });
  });
  it('asks to try again later on other errors', async () => {
    expect(await appError(params, outcomeResponse(500))).toStrictEqual({
      short: undefined,
      message: 'An error happened while loading the Patient with ID 0. Please try again later.'
    });
  });
});

describe('graphUrl test', () => {
  function graphParams(url: string): string[] {
    return new URL(url, 'http://localhost').searchParams.getAll('graph');
  }

  it('builds the URL of the $graph operation on the resource', () => {
    expect(graphUrl(params, 'graph-112815')).toBe('/fhir/Patient/0/$graph?graph=graph-112815');
  });
  for (const graph of [
    'http://example.com/GraphDefinition/a|1.0.0+build',
    'http://example.com/GraphDefinition/a&x=1',
    'http://example.com/GraphDefinition/a#fragment',
    'http://example.com/GraphDefinition/a%20b',
    'http://example.com/GraphDefinition/a b'
  ]) {
    it(`keeps the canonical ${graph} as the only graph param`, () => {
      const url = graphUrl(params, graph);
      expect(graphParams(url)).toStrictEqual([graph]);
      expect([...new URL(url, 'http://localhost').searchParams.keys()]).toStrictEqual(['graph']);
    });
  }
});

describe('startTypes test', () => {
  it('returns the type itself, DomainResource and Resource for a domain resource', () => {
    expect(startTypes('Observation')).toStrictEqual(['Observation', 'DomainResource', 'Resource']);
    expect(startTypes('Patient')).toStrictEqual(['Patient', 'DomainResource', 'Resource']);
  });
  for (const type of ['Binary', 'Bundle', 'Parameters']) {
    it(`leaves out DomainResource for ${type}`, () => {
      expect(startTypes(type)).toStrictEqual([type, 'Resource']);
    });
  }
});

describe('graphDefinitionsQuery test', () => {
  it('searches GraphDefinitions by all start types fitting the type in summary mode', () => {
    const query = graphDefinitionsQuery('Encounter');
    expect(query.getAll('start')).toStrictEqual(['Encounter,DomainResource,Resource']);
    expect(query.getAll('_summary')).toStrictEqual(['true']);
  });
});
