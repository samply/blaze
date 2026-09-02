<script lang="ts">
  import type { PageProps } from './$types';

  import { page } from '$app/state';

  import Breadcrumb from '$lib/breadcrumb.svelte';
  import BreadcrumbEntryHome from '$lib/breadcrumb/home.svelte';
  import BreadcrumbEntryType from '$lib/breadcrumb/type.svelte';
  import BreadcrumbEntryPage from '$lib/breadcrumb/page.svelte';

  import SearchForm from '../../search-form.svelte';
  import TotalCard from '$lib/total-card.svelte';
  import TotalBadge from '$lib/total-badge.svelte';
  import DurationBadge from '$lib/duration-badge.svelte';
  import EntryCard from '$lib/resource/entry-card.svelte';
  import NoResultsCard from '../../no-results-card.svelte';
  import ErrorCard from '$lib/error-card.svelte';
  import LoadingIndicator from '$lib/loading-indicator.svelte';
  import SummaryControl from '$lib/summary-control.svelte';
  import SummaryBanner from '$lib/summary-banner.svelte';
  import { bundleSummaryMode } from '$lib/resource/subsetted.js';

  let { data, params }: PageProps = $props();
</script>

<svelte:head>
  <title>{params.type} - Blaze</title>
</svelte:head>

<header class="mx-auto max-w-7xl sm:px-6 lg:px-8">
  <Breadcrumb>
    <BreadcrumbEntryHome />
    <BreadcrumbEntryType {...params} />
    <BreadcrumbEntryPage />
  </Breadcrumb>
</header>

<main class="mx-auto flex max-w-7xl flex-col sm:px-6 lg:px-8">
  <SearchForm searchMetadata={data.searchMetadata} type={params.type} />
  {#await data.streamed.bundle}
    <LoadingIndicator start={data.streamed.start} />
  {:then bundleWithDuration}
    {@const bundle = bundleWithDuration.bundle}
    {@const summaryMode = bundleSummaryMode(bundle)}

    <TotalCard {bundle}>
      <p class="py-1.5">
        {#if bundle.total !== undefined}
          <TotalBadge total={bundle.total} />
        {/if}
      </p>
      <p class="grow py-1.5">
        <DurationBadge duration={bundleWithDuration.duration} />
      </p>
      <!-- a paged result set is fixed to the summary mode of the search it belongs to -->
      <SummaryControl mode={summaryMode} fixed kind="search" url={page.url} />
    </TotalCard>

    <SummaryBanner mode={summaryMode} fixed url={page.url} />

    {#if bundle.fhirObjectEntry !== undefined && bundle.fhirObjectEntry.length > 0}
      {#each bundle.fhirObjectEntry as entry (entry.fullUrl)}
        <EntryCard {entry} />
      {/each}
    {:else if bundle.total === undefined}
      <NoResultsCard />
    {/if}
  {:catch error}
    <ErrorCard status={error.status} {error} />
  {/await}
</main>
