import { describe, expect, it } from 'vitest';
import { searchError } from './util.js';

function response(status: number, body: unknown = {}) {
  return new Response(JSON.stringify(body), { status });
}

function outcome(diagnostics?: string) {
  return { resourceType: 'OperationOutcome', issue: [{ diagnostics }] };
}

describe('searchError test', () => {
  const error = searchError({ type: 'Patient' });

  it('takes the message of a rejected search from the OperationOutcome', async () => {
    expect(await error(response(400, outcome('unknown search param')))).toEqual({
      short: 'Bad Request',
      message: 'unknown search param'
    });
  });

  it('falls back to a hint if the OperationOutcome has no diagnostics', async () => {
    expect(await error(response(422, outcome()))).toEqual({
      short: 'Unprocessable Content',
      message: 'Please check your search params.'
    });
  });

  it('reports a missing resource type on 404', async () => {
    expect(await error(response(404))).toEqual({
      short: 'Not Found',
      message: 'The resource type Patient was not found.'
    });
  });

  it('reports a generic failure on any other status', async () => {
    expect(await error(response(503))).toEqual({
      message: 'An error happened while loading the Patients. Please try again later.'
    });
  });
});
