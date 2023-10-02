import { sveltekit } from '@sveltejs/kit/vite';
import { defineConfig } from 'vite';
import type { IncomingMessage } from 'node:http';

function hasHeader(req: IncomingMessage, header: string): boolean {
	return req.headers.accept !== undefined && req.headers.accept.indexOf(header) >= 0;
}

function hasFormatSearchParam(req: IncomingMessage): boolean {
	if (req.url === undefined) {
		return false;
	}
	return new URL(req.url, 'http://localhost:8080').searchParams.has('_format');
}

export default defineConfig({
	plugins: [sveltekit()],

	server: {
		proxy: {
			'/fhir': {
				target: 'http://localhost:8080',
				changeOrigin: true,
				bypass: function (req) {
					if (
						!hasHeader(req, 'application/fhir+json') &&
						!hasHeader(req, 'application/json') &&
						!req.url?.startsWith('/fhir/__metadata') &&
						!hasFormatSearchParam(req)
					) {
						return req.url;
					}
				}
			}
		}
	}
});
