<script lang="ts">
  import { withSummaryParam, type SummaryMode } from '$lib/summary.js';
  import InfoTooltip from '$lib/tailwind/info-tooltip.svelte';
  import TooltipList from './tooltip/tooltip-list.svelte';
  import TooltipListItem from './tooltip/tooltip-list-item.svelte';
  import { ExclamationCircle } from 'svelte-heros-v2';

  type SummaryKind = 'search' | 'history';

  interface Props {
    /** The URL of the current result set, used to build the mode links. */
    url: URL;
    /**
     * The summary mode of this view, or undefined if the view gives no
     * evidence of its summary mode, in which case nothing is shown.
     */
    mode?: SummaryMode;
    /**
     * The kind of result set this control belongs to.
     */
    kind: SummaryKind;
    /**
     * Set if the mode of this result set can't be changed anymore, in which
     * case the control only shows the state.
     */
    fixed?: boolean;
  }

  let { url, mode, kind, fixed }: Props = $props();

  const summaryOptions = [
    { label: 'Count', mode: 'count' as const },
    { label: 'Summary', mode: 'summary' as const },
    { label: 'Full', mode: 'full' as const }
  ];

  const historyOptions = summaryOptions.slice(1);

  let options = $derived(kind === 'search' ? summaryOptions : historyOptions);

  const labelId = $props.id();
</script>

<!-- eslint-disable svelte/no-navigation-without-resolve -->

{#if mode !== undefined || !fixed}
  <div class="flex flex-none items-center gap-2">
    <span id={labelId} class="text-sm text-gray-500 dark:text-gray-400">Result</span>
    <nav
      aria-labelledby={labelId}
      class="inline-flex rounded-md bg-gray-100 p-0.5 ring-1 ring-gray-300 ring-inset dark:bg-gray-700 dark:ring-gray-500 {fixed
        ? 'opacity-75'
        : ''}"
    >
      {#each options as option (option.label)}
        {#if fixed}
          <span
            class="block rounded-md px-3 py-1 text-sm font-semibold text-gray-600 dark:text-gray-300 {mode ===
            option.mode
              ? 'bg-indigo-600 text-white dark:text-white'
              : ''}"
          >
            {option.label}
          </span>
        {:else}
          <a
            href={withSummaryParam(url, option.mode)}
            aria-current={mode === option.mode ? 'page' : undefined}
            data-sveltekit-keepfocus
            class="block rounded-md px-3 py-1 text-sm font-semibold text-gray-600 hover:text-gray-900 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 dark:text-gray-300 dark:hover:text-gray-100 {mode ===
            option.mode
              ? 'bg-indigo-600 text-white hover:text-white dark:text-white dark:hover:text-white'
              : ''}"
          >
            {option.label}
          </a>
        {/if}
      {/each}
    </nav>
    <InfoTooltip label="About result mode">
      <TooltipList>
        <TooltipListItem label="Count" value="_summary=count">
          Returns only the total, no resources.
        </TooltipListItem>
        <TooltipListItem label="Summary" value="_summary=true">
          Returns only the elements marked as summary in the FHIR resource definition.
        </TooltipListItem>
        <TooltipListItem label="Full" value="_summary=false">Returns all elements.</TooltipListItem>
      </TooltipList>
      {#if fixed}
        <div
          class="mt-2 flex justify-center gap-1.5 border-t border-gray-100 pt-2 dark:border-gray-700"
        >
          <ExclamationCircle class="size-4 shrink-0 text-blue-600 dark:text-blue-300" />
          <p class="text-xs text-blue-800 dark:text-blue-100">
            The mode of a paged result set is fixed. Start a new search to change it.
          </p>
        </div>
      {/if}
    </InfoTooltip>
  </div>
{/if}
