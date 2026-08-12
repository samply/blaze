<script lang="ts">
  import { ExclamationTriangle } from 'svelte-heros-v2';
  import { withSummaryParam, type SummaryMode } from '$lib/summary.js';

  interface Props {
    /** The URL of the current result set, used to build the escape-hatch link. */
    url: URL;
    /**
     * The summary mode of this view, or undefined if the view gives no
     * evidence of its summary mode, in which case nothing is shown.
     */
    mode?: SummaryMode;
    /**
     * Set if the mode of this result set can't be changed anymore, in which
     * case no escape hatch is offered.
     */
    fixed?: boolean;
  }

  let { url, mode, fixed }: Props = $props();

  let exitMode = $derived(mode === 'count' ? 'summary' : 'full') satisfies SummaryMode;
</script>

<!-- eslint-disable svelte/no-navigation-without-resolve -->

{#if mode === 'summary'}
  <div
    class="flex items-center gap-3 border-b border-gray-200 bg-yellow-50 px-4 py-3 sm:px-6 dark:border-gray-600 dark:bg-yellow-900/20"
  >
    <ExclamationTriangle class="size-5 shrink-0 text-yellow-600 dark:text-yellow-300" />
    <p class="grow text-sm text-yellow-800 dark:text-yellow-100">
      Summary mode — some elements are hidden to speed up results.
    </p>
    {#if !fixed}
      <a
        href={withSummaryParam(url, exitMode)}
        class="shrink-0 text-sm font-semibold text-indigo-600 hover:text-indigo-500 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 dark:text-indigo-300"
      >
        Show all elements
      </a>
    {/if}
  </div>
{:else if mode === 'count'}
  <div
    class="flex items-center gap-3 border-b border-gray-200 bg-yellow-50 px-4 py-3 sm:px-6 dark:border-gray-600 dark:bg-yellow-900/20"
  >
    <ExclamationTriangle class="size-5 shrink-0 text-yellow-600 dark:text-yellow-300" />
    <p class="grow text-sm text-yellow-800 dark:text-yellow-100">
      Count mode — no resources are returned, just the total.
    </p>
    {#if !fixed}
      <a
        href={withSummaryParam(url, exitMode)}
        class="shrink-0 text-sm font-semibold text-indigo-600 hover:text-indigo-500 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 dark:text-indigo-300"
      >
        Show resources
      </a>
    {/if}
  </div>
{/if}
