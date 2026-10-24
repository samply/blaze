import type { Session } from '#lib/server/session.js';

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
