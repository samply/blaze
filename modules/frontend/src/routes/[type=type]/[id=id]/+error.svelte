<script lang="ts">
  import { base } from '$app/paths';
  import { page } from '$app/state';

  import Breadcrumb from '$lib/breadcrumb.svelte';
  import BreadcrumbEntryHome from '$lib/breadcrumb/home.svelte';
  import BreadcrumbEntryType from '$lib/breadcrumb/type.svelte';

  import BreadcrumbEntryResource from '$lib/breadcrumb/resource.svelte';
  import ErrorCard from '$lib/error-card.svelte';
</script>

<svelte:head>
  <title>{page.params.type}/{page.params.id} - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl sm:px-6 lg:px-8">
  <Breadcrumb>
    <BreadcrumbEntryHome />
    <BreadcrumbEntryType />
    <BreadcrumbEntryResource last />
  </Breadcrumb>
</main>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
  <ErrorCard>
    {#if page.status === 404}
      <a href="{base}/{page.params.type}" class="text-sm font-semibold text-gray-900"
        >Go to {page.params.type}s <span aria-hidden="true">&rarr;</span></a
      >
    {:else if page.status === 410}
      <a
        href="{base}/{page.params.type}/{page.params.id}/_history"
        class="text-sm font-semibold text-gray-900"
        >Go to History <span aria-hidden="true">&rarr;</span></a
      >
    {/if}
  </ErrorCard>
</main>
