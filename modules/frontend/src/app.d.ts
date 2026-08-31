import type { Session } from '@auth/sveltekit';

declare module '@auth/core/jwt' {
  interface JWT {
    accessToken?: string;
    refreshToken?: string;
    expiresAt?: number;
  }
}

declare module '@auth/core/types' {
  interface Session {
    accessToken?: string;
    refreshToken?: string;
    expiresAt?: number;
  }
}

// See https://kit.svelte.dev/docs/types#app
// for information about these interfaces
declare global {
  namespace App {
    interface Error {
      short?: string;
      message: string;
      /**
       * The HTTP status the error was raised with.
       *
       * SvelteKit keeps the status next to the body for an error that fails a
       * load, but not for one that rejects a streamed promise: there only the
       * body reaches the `{:catch}` block. Carrying the status in the body as
       * well is what lets those blocks show it too.
       */
      status?: number;
    }
    interface Locals {
      session?: Session;
    }
    // interface PageData {}
    // interface Platform {}
  }
}

export {};
