<script lang="ts">
  import type { PageProps } from './$types';

  import BreadcrumbEntryHome from '$lib/breadcrumb/home.svelte';
  import BreadcrumbEntryType from '$lib/breadcrumb/type.svelte';

  import SearchForm from './search-form.svelte';
  import TotalCard from '$lib/total-card.svelte';
  import TotalBadge from '$lib/total-badge.svelte';
  import DurationBadge from '$lib/duration-badge.svelte';
  import EntryCard from '$lib/resource/entry-card.svelte';
  import NoResultsCard from './no-results-card.svelte';
  import ErrorCard from '$lib/error-card.svelte';
  import HistoryButton from './history-button.svelte';
  import MetadataButton from './metadata-button.svelte';
  import CodeSystemOperationDropdown from '../CodeSystem/operation-dropdown.svelte';
  import ValueSetOperationDropdown from '../ValueSet/operation-dropdown.svelte';
  import LoadingIndicator from '$lib/loading-indicator.svelte';

  let { data, params }: PageProps = $props();
</script>

<svelte:head>
  <title>{params.type} - Blaze</title>
</svelte:head>

<header class="mx-auto max-w-7xl sm:px-6 lg:px-8">
  <div class="flex gap-2 pl-8 pr-4 sm:pr-6 py-3.5 border-b border-gray-200 dark:border-gray-600">
    <nav class="flex flex-auto" aria-label="Breadcrumb">
      <ol class="flex items-center py-0.5 space-x-4">
        <BreadcrumbEntryHome />
        <BreadcrumbEntryType {...params} last />
      </ol>
    </nav>
    {#if params.type === 'CodeSystem'}
      <CodeSystemOperationDropdown />
    {:else if params.type === 'ValueSet'}
      <ValueSetOperationDropdown />
    {/if}
    <HistoryButton {...params} />
    <MetadataButton {...params} />
  </div>
</header>

<main class="mx-auto max-w-7xl sm:px-6 lg:px-8 flex flex-col">
  <SearchForm searchParams={data.searchParams} type={params.type} />
  {#await data.streamed.bundle}
    <LoadingIndicator start={data.streamed.start} />
  {:then bundleWithDuration}
    {@const bundle = bundleWithDuration.bundle}

    <TotalCard {bundle}>
      <p class="py-1.5">
        {#if bundle.total !== undefined}
          <TotalBadge total={bundle.total} />
        {/if}
      </p>
      <p class="grow py-1.5">
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
