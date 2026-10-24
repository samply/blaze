import { describe, it, expect, vi } from 'vitest';
import { createOriginCheck, warnAboutOrigin } from '#lib/server/origin.js';

describe('warnAboutOrigin test', () => {
  it('warns if ORIGIN is set', () => {
    const log = vi.fn();

    warnAboutOrigin('https://blaze.example.com', log);

    expect(log).toHaveBeenCalledOnce();
    expect(log.mock.calls[0][0]).toContain('ORIGIN is no longer supported');
    expect(log.mock.calls[0][0]).toContain('PROTOCOL_HEADER');
    expect(log.mock.calls[0][0]).toContain('HOST_HEADER');
  });

  it("doesn't warn if ORIGIN isn't set", () => {
    const log = vi.fn();

    warnAboutOrigin('', log);

    expect(log).not.toHaveBeenCalled();
  });
});

describe('createOriginCheck test', () => {
  // Behind a proxy that rewrites the Host header, the origin derived from the
  // request isn't the one the browser uses, which breaks sign-in, form actions
  // and the links in responses of the backend.
  it('logs if the origin of a request differs from ORIGIN', () => {
    const log = vi.fn();
    const check = createOriginCheck('https://blaze.example.com', log);

    check('https://frontend:3000');

    expect(log).toHaveBeenCalledOnce();
    expect(log.mock.calls[0][0]).toContain('https://frontend:3000');
    expect(log.mock.calls[0][0]).toContain('https://blaze.example.com');
  });

  it('logs only once', () => {
    const log = vi.fn();
    const check = createOriginCheck('https://blaze.example.com', log);

    check('https://frontend:3000');
    check('https://frontend:3000');

    expect(log).toHaveBeenCalledOnce();
  });

  it("doesn't log if the origin of a request equals ORIGIN", () => {
    const log = vi.fn();
    const check = createOriginCheck('https://blaze.example.com', log);

    check('https://blaze.example.com');

    expect(log).not.toHaveBeenCalled();
  });

  // ORIGIN used to be a base URL, so it may carry a trailing slash or a path.
  it('compares only the origin of ORIGIN', () => {
    const log = vi.fn();
    const check = createOriginCheck('https://blaze.example.com/', log);

    check('https://blaze.example.com');

    expect(log).not.toHaveBeenCalled();
  });

  it("doesn't log if ORIGIN isn't set", () => {
    const log = vi.fn();
    const check = createOriginCheck('', log);

    check('https://frontend:3000');

    expect(log).not.toHaveBeenCalled();
  });

  it("doesn't fail on an invalid ORIGIN", () => {
    const log = vi.fn();
    const check = createOriginCheck('blaze.example.com', log);

    check('https://blaze.example.com');

    expect(log).toHaveBeenCalledOnce();
  });
});
