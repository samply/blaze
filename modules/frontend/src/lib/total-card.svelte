<script lang="ts">
  import type { Snippet } from 'svelte';
  import type { Bundle } from 'fhir/r4';
  import { bundleLink } from './fhir.js';
  import { fade } from 'svelte/transition';
  import { quintIn } from 'svelte/easing';

  interface Props {
    bundle: Bundle;
    showFirstLink?: boolean;
    children?: Snippet;
  }

  let { bundle, showFirstLink = false, children }: Props = $props();

  let firstLinkUrl = $derived(bundleLink(bundle, 'first')?.url);
  let nextLinkUrl = $derived(bundleLink(bundle, 'next')?.url);
</script>

<div
  in:fade|global={{ duration: 300, easing: quintIn }}
  class="flex gap-2 px-4 py-5 sm:px-6 border-b border-gray-200"
>
  {@render children?.()}
  {#if showFirstLink && firstLinkUrl}
    <a
      href={firstLinkUrl}
      class="flex-none rounded-md bg-white px-3 py-2 text-sm font-semibold text-gray-900 ring-1 ring-inset ring-gray-300 hover:bg-gray-50"
      >First</a
    >
  {/if}
  {#if nextLinkUrl}
    <a
      href={nextLinkUrl}
      class="flex-none w-20 inline-flex items-center gap-x-1.5 rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white hover:bg-indigo-500 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
    >
      Next
      <svg class="-mr-0.5 h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
        <path
          fill-rule="evenodd"
          d="M3 10a.75.75 0 01.75-.75h10.638L10.23 5.29a.75.75 0 111.04-1.08l5.5 5.25a.75.75 0 010 1.08l-5.5 5.25a.75.75 0 11-1.04-1.08l4.158-3.96H3.75A.75.75 0 013 10z"
          clip-rule="evenodd"
        />
      </svg>
    </a>
  {/if}
  {#if showFirstLink && firstLinkUrl && !nextLinkUrl}
    <button
      type="button"
      disabled
      class="flex-none w-20 inline-flex items-center gap-x-1.5 rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white opacity-50 enabled:cursor-pointer"
    >
      Next
      <svg class="-mr-0.5 h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
        <path
          fill-rule="evenodd"
          d="M3 10a.75.75 0 01.75-.75h10.638L10.23 5.29a.75.75 0 111.04-1.08l5.5 5.25a.75.75 0 010 1.08l-5.5 5.25a.75.75 0 11-1.04-1.08l4.158-3.96H3.75A.75.75 0 013 10z"
          clip-rule="evenodd"
        />
      </svg>
    </button>
  {/if}
</div>
