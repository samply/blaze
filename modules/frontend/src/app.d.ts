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
    }
    interface Locals {
      session?: Session;
    }
    // interface PageData {}
    // interface Platform {}
  }
}

export {};
