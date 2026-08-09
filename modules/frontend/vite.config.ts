import tailwindcss from '@tailwindcss/vite';
import { defineConfig } from 'vitest/config';
import adapter from '@sveltejs/adapter-node';
import { sveltekit } from '@sveltejs/kit/vite';

export default defineConfig({
  plugins: [
    tailwindcss(),
    sveltekit({
      compilerOptions: {
        // Force runes mode for the project, except for libraries. Can be removed in svelte 6.
        runes: ({ filename }) =>
          filename.split(/[/\\]/).includes('node_modules') ? undefined : true
      },
      adapter: adapter(),
      paths: {
        base: '/fhir'
      },
      csp: {
        directives: {
          'script-src': ['self'],
          'object-src': ['none'],
          'base-uri': ['none'],
          'frame-ancestors': ['none']
        }
      }
    })
  ],
  test: {
    expect: { requireAssertions: true },
    include: ['src/**/*.{test,spec}.{js,ts}']
  }
});
