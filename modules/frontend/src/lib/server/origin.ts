/**
 * Checks for the `ORIGIN` environment variable, which is no longer supported.
 *
 * Up to SvelteKit 2, adapter-node took the origin of the frontend from
 * `ORIGIN` and ignored `PROTOCOL_HEADER` and `HOST_HEADER` if it was set. Now
 * the origin is always derived from the request. Deployments that only set
 * `ORIGIN` behind a proxy that rewrites the `Host` header get a wrong origin,
 * which breaks sign-in, form actions and the links in responses of the
 * backend.
 */

const howToFix =
  'The origin of the frontend is derived from the request instead: the Host header, or the ' +
  'header named in HOST_HEADER, and the header named in PROTOCOL_HEADER, with https as default. ' +
  'Behind a reverse proxy, set PROTOCOL_HEADER=x-forwarded-proto and ' +
  'HOST_HEADER=x-forwarded-host, or let the proxy keep the Host header, and remove ORIGIN.';

const plainHttp =
  'ORIGIN uses http, but without a reverse proxy that sets the protocol via ' +
  'PROTOCOL_HEADER, the frontend assumes https, so it has to be reached through such a proxy.';

/**
 * Logs a warning via `log` if `origin`, the value of `ORIGIN`, is set.
 *
 * An `http` origin can't be reproduced without a reverse proxy, because the
 * protocol defaults to `https` if no header names it.
 */
export function warnAboutOrigin(origin: string, log: (message: string) => void): void {
  if (origin) {
    const message = `The environment variable ORIGIN is no longer supported and is ignored. ${howToFix}`;
    log(origin.startsWith('http:') ? `${message} ${plainHttp}` : message);
  }
}

function originOf(url: string): string {
  try {
    return new URL(url).origin;
  } catch {
    return url;
  }
}

const loopbackHosts = new Set(['localhost', '127.0.0.1', '[::1]']);

/**
 * Requests to a loopback host are health checks or local tools, never browser
 * traffic through a reverse proxy.
 */
function isLoopback(requestOrigin: string): boolean {
  return loopbackHosts.has(new URL(requestOrigin).hostname);
}

/**
 * Returns a function that logs an error via `log` the first time it is called
 * with a request origin that differs from `origin`, the value of `ORIGIN`.
 *
 * Request origins of loopback hosts are ignored, so that the health check of
 * the container doesn't use up the error.
 */
export function createOriginCheck(
  origin: string,
  log: (message: string) => void
): (requestOrigin: string) => void {
  let reported = !origin;

  return (requestOrigin) => {
    if (!reported && requestOrigin !== originOf(origin) && !isLoopback(requestOrigin)) {
      reported = true;
      log(
        `The origin ${requestOrigin} derived from a request differs from ORIGIN=${origin}, ` +
          `which is no longer supported. Sign-in, form actions and links in responses of the ` +
          `backend will fail. ${howToFix}`
      );
    }
  };
}
