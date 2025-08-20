<script lang="ts">
  import type { PageProps } from './$types';

  import TotalCard from '$lib/total-card.svelte';
  import TotalBadge from '$lib/total-badge.svelte';
  import EntryCard from '$lib/resource/entry-card.svelte';

  let { data }: PageProps = $props();
</script>

<svelte:head>
  <title>History - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl sm:px-6 lg:px-8 flex flex-col">
  <TotalCard bundle={data.bundle}>
    <p class="grow py-1.5">
      {#if data.bundle.total !== undefined}
        <TotalBadge total={data.bundle.total} />
      {/if}
    </p>
  </TotalCard>

  {#if data.bundle.fhirObjectEntry !== undefined && data.bundle.fhirObjectEntry.length > 0}
    {#each data.bundle.fhirObjectEntry as entry ((entry.fullUrl || '') + (entry.response?.etag || ''))}
      <EntryCard {entry} />
    {/each}
    <TotalCard bundle={data.bundle}>
      <p class="grow"></p>
    </TotalCard>
  {:else}
    <div class="overflow-hidden text-center py-10 text-3xl text-gray-700 dark:text-gray-300">
      The history is empty
    </div>
  {/if}
</main>
