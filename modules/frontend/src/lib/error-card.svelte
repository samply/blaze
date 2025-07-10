<script lang="ts">
  import type { Snippet } from 'svelte';

  import { base } from '$app/paths';
  import { page } from '$app/state';

  interface Props {
    status?: number;
    error?: {
      short?: string;
      message: string;
    } | null;
    homeHref?: string;
    homeMsg?: string;
    children?: Snippet;
  }

  let {
    status = page.status,
    error = page.error,
    homeHref = base,
    homeMsg = 'Go back home',
    children
  }: Props = $props();
</script>

<div class="text-center overflow-hidden py-20">
  <p class="text-base font-semibold text-indigo-600">{status}</p>
  <h1 class="mt-4 text-3xl font-bold tracking-tight text-gray-900 sm:text-5xl">
    {error?.short ?? 'Error'}
  </h1>
  <p class="mt-6 text-base leading-7 text-gray-600">
    {error?.message}
  </p>
  <div class="mt-10 flex items-center justify-center gap-x-6">
    <a
      href={homeHref}
      class="rounded-md bg-indigo-600 px-3.5 py-2.5 text-sm font-semibold text-white hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
      >{homeMsg}</a
    >
    {@render children?.()}
  </div>
</div>
