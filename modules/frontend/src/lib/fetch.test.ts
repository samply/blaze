import { describe, it, expect, vi } from 'vitest';
import { fetchFhir, fetchJson, loadError, resourceError } from '$lib/fetch.js';

function response(status: number, body: unknown = {}) {
  return new Response(JSON.stringify(body), { status });
}

function fetchReturning(res: Response) {
  return vi.fn(async () => res);
}

describe('loadError test', () => {
  const messages = { 404: 'not found', 410: 'gone', default: 'default' };

  it('returns the message of the status together with its label', async () => {
    expect(await loadError(messages)(response(404))).toEqual({
      short: 'Not Found',
      message: 'not found'
    });
  });

  it('returns the message of every listed status', async () => {
    expect(await loadError(messages)(response(410))).toEqual({ short: 'Gone', message: 'gone' });
  });

  it('returns the default message for an unlisted status', async () => {
    expect(await loadError({ 404: 'not found', default: 'default' })(response(410))).toEqual({
      message: 'default'
    });
  });

  it('returns the default message without a label', async () => {
    expect(await loadError(messages)(response(503))).toEqual({ message: 'default' });
  });

  it('takes a message from the response', async () => {
    const message = async (res: Response) => ((await res.json()) as { msg: string }).msg;

    expect(
      await loadError({ 400: message, default: 'default' })(response(400, { msg: 'boom' }))
    ).toEqual({ short: 'Bad Request', message: 'boom' });
  });

  it('labels a status beyond the not-found ones', async () => {
    expect(await loadError({ 400: 'bad request', default: 'default' })(response(400))).toEqual({
      short: 'Bad Request',
      message: 'bad request'
    });
  });
});

describe('resourceError test', () => {
  it('reports a missing resource on 404', async () => {
    expect(await resourceError('Patient', '0')(response(404))).toEqual({
      short: 'Not Found',
      message: 'The Patient with ID 0 was not found.'
    });
  });

  it('points at the history on 410', async () => {
    expect(await resourceError('Patient', '0')(response(410))).toEqual({
      short: 'Gone',
      message: 'The Patient with ID 0 was deleted. Please look into the history.'
    });
  });

  it('reports a generic failure on any other status', async () => {
    expect(await resourceError('Patient', '0')(response(503))).toEqual({
      message: 'An error happened while loading the Patient with ID 0. Please try again later.'
    });
  });
});

describe('fetchFhir test', () => {
  it('returns the parsed body of a successful response', async () => {
    const fetch = fetchReturning(response(200, { resourceType: 'Patient', id: '0' }));

    expect(await fetchFhir(fetch, '/fhir/Patient/0', { error: 'error' })).toEqual({
      resourceType: 'Patient',
      id: '0'
    });
  });

  it('accepts FHIR JSON', async () => {
    const fetch = fetchReturning(response(200));

    await fetchFhir(fetch, '/fhir/Patient/0', { error: 'error' });

    expect(fetch).toHaveBeenCalledWith('/fhir/Patient/0', {
      headers: { Accept: 'application/fhir+json' }
    });
  });

  it('fails with the status and the given error body on an unsuccessful response', async () => {
    const fetch = fetchReturning(response(503));

    await expect(fetchFhir(fetch, '/fhir/Patient/0', { error: 'error' })).rejects.toMatchObject({
      status: 503,
      body: { status: 503, message: 'error' }
    });
  });

  it('fails with the given App.Error on an unsuccessful response', async () => {
    const fetch = fetchReturning(response(503));
    const body = { short: 'Service Unavailable', message: 'error' };

    await expect(fetchFhir(fetch, '/fhir/Patient/0', { error: body })).rejects.toMatchObject({
      status: 503,
      body: { ...body, status: 503 }
    });
  });

  it('keeps the status the error body carries itself', async () => {
    const fetch = fetchReturning(response(503));

    await expect(
      fetchFhir(fetch, '/fhir/Patient/0', { error: { status: 500, message: 'error' } })
    ).rejects.toMatchObject({ status: 503, body: { status: 500, message: 'error' } });
  });

  it('resolves a function error body with the response', async () => {
    const fetch = fetchReturning(response(404));

    await expect(
      fetchFhir(fetch, '/fhir/Patient/0', { error: resourceError('Patient', '0') })
    ).rejects.toMatchObject({
      status: 404,
      body: { status: 404, short: 'Not Found', message: 'The Patient with ID 0 was not found.' }
    });
  });
});

describe('fetchJson test', () => {
  it('returns the parsed body of a successful response', async () => {
    const fetch = fetchReturning(response(200, { name: 'index' }));

    expect(await fetchJson(fetch, '/fhir/__admin/dbs', { error: 'error' })).toEqual({
      name: 'index'
    });
  });

  it('accepts plain JSON', async () => {
    const fetch = fetchReturning(response(200));

    await fetchJson(fetch, '/fhir/__admin/dbs', { error: 'error' });

    expect(fetch).toHaveBeenCalledWith('/fhir/__admin/dbs', {
      headers: { Accept: 'application/json' }
    });
  });

  it('fails with the status and the given error body on an unsuccessful response', async () => {
    const fetch = fetchReturning(response(503));

    await expect(fetchJson(fetch, '/fhir/__admin/dbs', { error: 'error' })).rejects.toMatchObject({
      status: 503,
      body: { status: 503, message: 'error' }
    });
  });
});
