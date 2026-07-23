<script lang="ts">
  import type { PageProps } from './$types';

  import { page } from '$app/state';

  import Breadcrumb from '$lib/breadcrumb.svelte';
  import BreadcrumbEntryHome from '$lib/breadcrumb/home.svelte';
  import BreadcrumbEntryType from '$lib/breadcrumb/type.svelte';

  import SearchForm from './search-form.svelte';
  import ErrorCard from '$lib/error-card.svelte';
  import { defaultSummarySettings, isSummaryEnabled, type SummarySettings } from '$lib/summary.js';

  let { params }: PageProps = $props();

  let summary = $derived(
    isSummaryEnabled(
      (page.data.summarySettings as SummarySettings | undefined) ?? defaultSummarySettings,
      params.type
    )
  );
</script>

<svelte:head>
  <title>{params.type} - Blaze</title>
</svelte:head>

<header class="mx-auto max-w-7xl sm:px-6 lg:px-8">
  <Breadcrumb>
    <BreadcrumbEntryHome />
    <BreadcrumbEntryType {...params} />
  </Breadcrumb>
</header>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8 flex flex-col">
  <SearchForm searchParams={page.data.searchParams || []} type={params.type} {summary} />
  <ErrorCard />
</main>
