export type Env = {
  /**
   * The origin serving the rendering of the guide, if it isn't the host the
   * Worker answers on, e.g. when testing the Worker on its workers.dev host.
   */
  ORIGIN?: string;
};

/**
 * Returns the origin serving the rendering of the guide: the configured one,
 * or the one of `request`, which is the case in production, where the Worker
 * answers on the host of the guide itself.
 */
export function originOf(request: Request, env: Env): string {
  return new URL(env.ORIGIN || request.url).origin;
}
