import adapter from '@sveltejs/adapter-node';
import { vitePreprocess } from '@sveltejs/vite-plugin-svelte';

/** @type {import('@sveltejs/kit').Config} */
const config = {
  // Consult https://kit.svelte.dev/docs/integrations#preprocessors
  // for more information about preprocessors
  preprocess: vitePreprocess(),

  kit: {
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
  }
};

export default config;
