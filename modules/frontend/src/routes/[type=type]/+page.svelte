<script lang="ts">
  import type { PageProps } from './$types';

  import { onMount } from 'svelte';
  import { page } from '$app/state';
  import { fade, slide } from 'svelte/transition';

  import BreadcrumbEntryHome from '$lib/breadcrumb/home.svelte';
  import BreadcrumbEntryType from '$lib/breadcrumb/type.svelte';

  import SearchForm from './search-form.svelte';
  import TotalCard from '$lib/total-card.svelte';
  import TotalBadge from '$lib/total-badge.svelte';
  import DurationBadge from '$lib/duration-badge.svelte';
  import EntryCard from '$lib/history/entry-card.svelte';
  import NoResultsCard from './no-results-card.svelte';
  import ErrorCard from '$lib/error-card.svelte';
  import HistoryButton from './history-button.svelte';
  import MetadataButton from './metadata-button.svelte';
  import CodeSystemOperationDropdown from '../CodeSystem/operation-dropdown.svelte';
  import ValueSetOperationDropdown from '../ValueSet/operation-dropdown.svelte';

  let { data }: PageProps = $props();

  let duration = $state(0);

  onMount(() => {
    const interval = setInterval(() => {
      duration = Date.now() - data.streamed.start;
    }, 100);
    return () => clearInterval(interval);
  });
</script>

<svelte:head>
  <title>{page.params.type} - Blaze</title>
</svelte:head>

<header class="mx-auto max-w-7xl sm:px-6 lg:px-8">
  <div class="flex gap-2 pl-8 pr-4 sm:pr-6 py-3.5 border-b border-gray-200">
    <nav class="flex flex-auto" aria-label="Breadcrumb">
      <ol class="flex items-center py-0.5 space-x-4">
        <BreadcrumbEntryHome />
        <BreadcrumbEntryType last />
      </ol>
    </nav>
    {#if page.params.type === 'CodeSystem'}
      <CodeSystemOperationDropdown />
    {:else if page.params.type === 'ValueSet'}
      <ValueSetOperationDropdown />
    {/if}
    <HistoryButton />
    <MetadataButton />
  </div>
</header>

<main class="mx-auto max-w-7xl sm:px-6 lg:px-8 flex flex-col">
  <SearchForm searchParams={data.searchParams} />
  {#await data.streamed.bundle}
    {#if duration > 300}
      <div
        in:fade|global={{ duration: 200 }}
        out:slide|global={{ duration: 200 }}
        class="text-center px-4 py-5 sm:px-6 text-gray-700"
      >
        <code>
          loading...
          {(duration / 1000).toLocaleString(undefined, {
            minimumFractionDigits: 1,
            maximumFractionDigits: 1
          })}
          s
        </code>
      </div>
    {/if}
  {:then bundleWithDuration}
    {@const bundle = bundleWithDuration.bundle}

    <TotalCard {bundle}>
      <p class="py-1.5">
        {#if bundle.total !== undefined}
          <TotalBadge total={bundle.total} />
        {/if}
      </p>
      <p class="flex-grow py-1.5">
        <DurationBadge duration={bundleWithDuration.duration} />
      </p>
    </TotalCard>

    {#if bundle.fhirObjectEntry !== undefined && bundle.fhirObjectEntry.length > 0}
      {#each bundle.fhirObjectEntry as entry (entry.fullUrl)}
        <EntryCard {entry} />
      {/each}
    {:else if bundle.total === undefined}
      <NoResultsCard />
    {/if}
  {:catch error}
    <ErrorCard status={error.status} error={error.body} />
  {/await}
</main>
