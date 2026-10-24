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

/**
 * Logs a warning via `log` if `origin`, the value of `ORIGIN`, is set.
 */
export function warnAboutOrigin(origin: string, log: (message: string) => void): void {
  if (origin) {
    log(`The environment variable ORIGIN is no longer supported and is ignored. ${howToFix}`);
  }
}

function originOf(url: string): string {
  try {
    return new URL(url).origin;
  } catch {
    return url;
  }
}

/**
 * Returns a function that logs an error via `log` the first time it is called
 * with a request origin that differs from `origin`, the value of `ORIGIN`.
 */
export function createOriginCheck(
  origin: string,
  log: (message: string) => void
): (requestOrigin: string) => void {
  let reported = !origin;

  return (requestOrigin) => {
    if (!reported && requestOrigin !== originOf(origin)) {
      reported = true;
      log(
        `The origin ${requestOrigin} derived from a request differs from ORIGIN=${origin}, ` +
          `which is no longer supported. Sign-in, form actions and links in responses of the ` +
          `backend will fail. ${howToFix}`
      );
    }
  };
}
