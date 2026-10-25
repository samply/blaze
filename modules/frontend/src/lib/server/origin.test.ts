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

  // Without a reverse proxy setting the protocol via PROTOCOL_HEADER, the
  // origin always uses https, so a plain HTTP setup can't be reproduced.
  it('explains that an http ORIGIN needs a reverse proxy', () => {
    const log = vi.fn();

    warnAboutOrigin('http://blaze-host:3000', log);

    expect(log.mock.calls[0][0]).toContain('without a reverse proxy');
  });

  it("doesn't explain that for an https ORIGIN", () => {
    const log = vi.fn();

    warnAboutOrigin('https://blaze.example.com', log);

    expect(log.mock.calls[0][0]).not.toContain('without a reverse proxy');
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

  // Requests to a loopback host are health checks or local tools, never
  // browser traffic through a reverse proxy.
  it.each(['http://localhost:3000', 'https://127.0.0.1:3000', 'https://[::1]:3000'])(
    "doesn't log for the loopback origin %s",
    (requestOrigin) => {
      const log = vi.fn();
      const check = createOriginCheck('https://blaze.example.com', log);

      check(requestOrigin);

      expect(log).not.toHaveBeenCalled();
    }
  );

  // The health check of the container usually comes first, so it must not
  // use up the one error a real mismatch gets.
  it('still logs a mismatch after a request to a loopback host', () => {
    const log = vi.fn();
    const check = createOriginCheck('https://blaze.example.com', log);

    check('https://127.0.0.1:3000');
    check('https://frontend:3000');

    expect(log).toHaveBeenCalledOnce();
    expect(log.mock.calls[0][0]).toContain('https://frontend:3000');
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
