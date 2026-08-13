import { describe, it, expect, vi } from 'vitest';
import { isHttpError, isRedirect } from '@sveltejs/kit';
import { fetchJson, fetchJsonStreamed, notFoundError, resourceNotFoundError } from '$lib/fetch.js';
import { signInPath } from '$lib/sign-in.js';

describe('notFoundError test', () => {
  const messages = { notFound: 'not found msg', gone: 'gone msg', default: 'default msg' };

  it('reports 404 specifically', () => {
    expect(notFoundError({ status: 404 }, messages)).toStrictEqual({
      short: 'Not Found',
      message: 'not found msg'
    });
  });

  it('reports 410 specifically when a gone message is given', () => {
    expect(notFoundError({ status: 410 }, messages)).toStrictEqual({
      short: 'Gone',
      message: 'gone msg'
    });
  });

  it('falls back to the default message on 410 without a gone message', () => {
    expect(
      notFoundError({ status: 410 }, { notFound: 'not found msg', default: 'default msg' })
    ).toStrictEqual({
      short: undefined,
      message: 'default msg'
    });
  });

  it('falls back to the default message on any other status', () => {
    expect(notFoundError({ status: 500 }, messages)).toStrictEqual({
      short: undefined,
      message: 'default msg'
    });
  });
});

describe('resourceNotFoundError test', () => {
  it('reports 404 with a type- and ID-specific message', () => {
    expect(resourceNotFoundError({ status: 404 }, 'Patient', '123')).toStrictEqual({
      short: 'Not Found',
      message: 'The Patient with ID 123 was not found.'
    });
  });

  it('reports 410 as Gone, pointing to the history', () => {
    expect(resourceNotFoundError({ status: 410 }, 'Patient', '123')).toStrictEqual({
      short: 'Gone',
      message: 'The Patient with ID 123 was deleted. Please look into the history.'
    });
  });

  it('falls back to a generic message on any other status', () => {
    expect(resourceNotFoundError({ status: 500 }, 'Patient', '123')).toStrictEqual({
      short: undefined,
      message: 'An error happened while loading the Patient with ID 123. Please try again later.'
    });
  });
});

describe('fetchJson test', () => {
  it('returns the parsed JSON body on success', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ foo: 'bar' })));

    await expect(fetchJson(fetch, 'http://localhost/foo', 'error')).resolves.toStrictEqual({
      foo: 'bar'
    });
  });

  it('requests the FHIR JSON media type by default', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response('{}'));

    await fetchJson(fetch, 'http://localhost/foo', 'error');

    expect(fetch).toHaveBeenCalledWith('http://localhost/foo', {
      headers: { Accept: 'application/fhir+json' }
    });
  });

  it('requests a custom media type when given', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response('{}'));

    await fetchJson(fetch, 'http://localhost/foo', 'error', { Accept: 'application/json' });

    expect(fetch).toHaveBeenCalledWith('http://localhost/foo', {
      headers: { Accept: 'application/json' }
    });
  });

  it('aborts with a SvelteKit error carrying the given error body on a non-ok response', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 500 }));

    try {
      await fetchJson(fetch, 'http://localhost/foo', 'something went wrong');
      expect.fail('expected fetchJson to throw');
    } catch (e) {
      expect(isHttpError(e, 500)).toBe(true);
      expect((e as { body: unknown }).body).toStrictEqual({ message: 'something went wrong' });
    }
  });

  it('resolves a function error body with the response', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 404 }));

    try {
      await fetchJson(fetch, 'http://localhost/foo', (res) => `status was ${res.status}`);
      expect.fail('expected fetchJson to throw');
    } catch (e) {
      expect((e as { body: unknown }).body).toStrictEqual({ message: 'status was 404' });
    }
  });

  it('redirects to the sign-in page on a 401 without calling the error body', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 401 }));
    const errorBody = vi.fn();

    try {
      await fetchJson(fetch, 'http://localhost/foo', errorBody);
      expect.fail('expected fetchJson to throw');
    } catch (e) {
      expect(isRedirect(e)).toBe(true);
      expect((e as { status: number }).status).toBe(307);
      expect((e as { location: string }).location).toBe(signInPath);
    }

    expect(errorBody).not.toHaveBeenCalled();
  });

  // Defense in depth: a response status outside 400-599 would make
  // SvelteKit's own `error()` throw an "invalid status code" error instead,
  // turning into an unrelated 500 with no useful body. Clamping to 500
  // keeps that a well-formed SvelteKit error instead. No known code path
  // delivers such a status today — see the comment on `abort()` in
  // `fetch.ts`.
  it('clamps a non-ok response status outside 400-599 to 500', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 307 }));

    try {
      await fetchJson(fetch, 'http://localhost/foo', 'something went wrong');
      expect.fail('expected fetchJson to throw');
    } catch (e) {
      expect(isHttpError(e, 500)).toBe(true);
      expect((e as { body: unknown }).body).toStrictEqual({ message: 'something went wrong' });
    }
  });
});

describe('fetchJsonStreamed test', () => {
  it('returns the parsed JSON body on success', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(JSON.stringify({ foo: 'bar' })));

    await expect(fetchJsonStreamed(fetch, 'http://localhost/foo', 'error')).resolves.toStrictEqual({
      foo: 'bar'
    });
  });

  it('aborts with a SvelteKit error on a non-ok response', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 500 }));

    try {
      await fetchJsonStreamed(fetch, 'http://localhost/foo', 'something went wrong');
      expect.fail('expected fetchJsonStreamed to throw');
    } catch (e) {
      expect(isHttpError(e, 500)).toBe(true);
      expect((e as { body: unknown }).body).toStrictEqual({ message: 'something went wrong' });
    }
  });

  // `goto` (and therefore the sign-in redirect) is unavailable outside the
  // browser, which is what every other test in this file runs under too —
  // see `redirectIfSessionExpired` in `$lib/sign-in-navigation.ts`. A 401
  // therefore falls through to the normal error handling here, exactly like
  // any other non-ok response.
  it('falls through to the normal error handling on a 401 outside the browser', async () => {
    const fetch = vi.fn().mockResolvedValue(new Response(null, { status: 401 }));

    try {
      await fetchJsonStreamed(fetch, 'http://localhost/foo', 'session expired');
      expect.fail('expected fetchJsonStreamed to throw');
    } catch (e) {
      expect(isHttpError(e, 401)).toBe(true);
      expect((e as { body: unknown }).body).toStrictEqual({ message: 'session expired' });
    }
  });
});
