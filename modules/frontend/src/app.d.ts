// See https://kit.svelte.dev/docs/types#app
// for information about these interfaces
declare global {
	namespace App {
		interface Error {
			short?: string;
			message: string;
		}
		// interface Locals {}
		// interface PageData {}
		// interface Platform {}
	}
}

export {};
