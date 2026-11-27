/**
 * Checks the origin of the frontend derived from requests, which has to be
 * the one the browser uses, and the `ORIGIN` environment variable, which is
 * no longer supported.
 *
 * Up to SvelteKit 2, adapter-node took the origin of the frontend from
 * `ORIGIN` and ignored `PROTOCOL_HEADER` and `HOST_HEADER` if it was set. Now
 * the origin is always derived from the request. Deployments that only set
 * `ORIGIN` behind a proxy that rewrites the `Host` header get a wrong origin,
 * which breaks sign-in, form actions and the links in responses of the
 * backend.
 */

const derivation =
  'the Host header, or the header named in HOST_HEADER, and the header named in ' +
  'PROTOCOL_HEADER, with https as default';

const howToFix =
  `The origin of the frontend is derived from the request instead: ${derivation}. ` +
  'Behind a reverse proxy, set PROTOCOL_HEADER=x-forwarded-proto and ' +
  'HOST_HEADER=x-forwarded-host, or let the proxy keep the Host header, and remove ORIGIN.';

const howToForward =
  `The origin of the frontend is derived from the request: ${derivation}. ` +
  'Let the reverse proxy forward the host the browser uses including a non-standard port, ' +
  'as the Host header does, for example with nginx ' +
  '`proxy_set_header X-Forwarded-Host $http_host` instead of `$host`, and set ' +
  'HOST_HEADER=x-forwarded-host. Set PROTOCOL_HEADER=x-forwarded-proto, if the proxy is ' +
  'reached via http.';

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

/**
 * Returns true if the value of an `Origin` header is a single origin.
 *
 * RFC 6454 also allows `null` and a space-separated list of origins. The
 * Fetch standard, which browsers implement, supplants it and allows no list.
 */
function isSingleOrigin(origin: string): boolean {
  return origin !== 'null' && !/\s/.test(origin);
}

/**
 * Returns the origin the browser uses, if the request comes from a page of
 * the same origin, according to the browser.
 *
 * Only then the `Origin` header has to equal the origin derived from the
 * request. Cross-site requests legitimately have a foreign origin and
 * requests without `Sec-Fetch-Site` can't be told apart from them. Browsers
 * send no `Origin` header on same-origin GET requests.
 */
function sameOriginBrowserOrigin(headers: Headers): string | undefined {
  const origin = headers.get('origin');
  if (headers.get('sec-fetch-site') === 'same-origin' && origin && isSingleOrigin(origin)) {
    return origin;
  }
}

/**
 * Returns a function that logs an error via `log` the first time it is called
 * with the `headers` of a same-origin request whose `Origin` header differs
 * from `derivedOrigin`, the origin derived from the request.
 *
 * Such a mismatch happens behind a reverse proxy that doesn't forward the
 * host the browser uses, for example dropping a non-standard port. It breaks
 * sign-in, form actions and the links in responses of the backend, and the
 * error Better Auth logs points to its `trustedOrigins` instead.
 */
export function createBrowserOriginCheck(
  log: (message: string) => void
): (headers: Headers, derivedOrigin: string) => void {
  let reported = false;

  return (headers, derivedOrigin) => {
    const browserOrigin = sameOriginBrowserOrigin(headers);

    if (!reported && browserOrigin && browserOrigin !== derivedOrigin) {
      reported = true;
      log(
        `The browser uses the origin ${browserOrigin}, but the origin derived from the request ` +
          `is ${derivedOrigin}. Sign-in, form actions and links in responses of the backend ` +
          `will fail. ${howToForward}`
      );
    }
  };
}
