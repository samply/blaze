<script lang="ts">
  import type { PageProps } from './$types';

  import { page } from '$app/state';

  import TotalCard from '$lib/total-card.svelte';
  import TotalBadge from '$lib/total-badge.svelte';
  import EntryCard from '$lib/resource/entry-card.svelte';
  import SummaryControl from '$lib/summary-control.svelte';
  import SummaryBanner from '$lib/summary-banner.svelte';
  import { bundleSummaryMode } from '$lib/resource/subsetted.js';

  let { data }: PageProps = $props();

  let summaryMode = $derived(bundleSummaryMode(data.bundle));
</script>

<svelte:head>
  <title>History - Blaze</title>
</svelte:head>

<main class="mx-auto flex max-w-7xl flex-col sm:px-6 lg:px-8">
  <TotalCard bundle={data.bundle}>
    <p class="grow py-1.5">
      {#if data.bundle.total !== undefined}
        <TotalBadge total={data.bundle.total} />
      {/if}
    </p>
    <!-- a paged result set is fixed to the summary mode of the history it belongs to -->
    <SummaryControl mode={summaryMode} fixed kind="history" url={page.url} />
  </TotalCard>

  <SummaryBanner mode={summaryMode} fixed url={page.url} />

  {#if data.bundle.fhirObjectEntry !== undefined && data.bundle.fhirObjectEntry.length > 0}
    {#each data.bundle.fhirObjectEntry as entry ((entry.fullUrl || '') + (entry.response?.etag || ''))}
      <EntryCard {entry} />
    {/each}
    <TotalCard bundle={data.bundle}>
      <p class="grow"></p>
    </TotalCard>
  {:else}
    <div class="overflow-hidden py-10 text-center text-3xl text-gray-700 dark:text-gray-300">
      The history is empty
    </div>
  {/if}
</main>
