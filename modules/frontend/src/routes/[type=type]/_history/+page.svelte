<script lang="ts">
  import type { PageProps } from './$types';

  import Breadcrumb from '$lib/breadcrumb.svelte';
  import BreadcrumbEntryHome from '$lib/breadcrumb/home.svelte';
  import BreadcrumbEntryType from '$lib/breadcrumb/type.svelte';
  import BreadcrumbEntryHistory from '$lib/breadcrumb/type-history.svelte';

  import TotalCard from '$lib/total-card.svelte';
  import TotalBadge from '$lib/total-badge.svelte';
  import EntryCard from '$lib/history/entry-card.svelte';

  let { data, params }: PageProps = $props();
</script>

<svelte:head>
  <title>History - {params.type} - Blaze</title>
</svelte:head>

<header class="mx-auto max-w-7xl sm:px-6 lg:px-8">
  <Breadcrumb>
    <BreadcrumbEntryHome />
    <BreadcrumbEntryType {...params} />
    <BreadcrumbEntryHistory {...params} last />
  </Breadcrumb>
</header>

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
  {/if}
</main>
