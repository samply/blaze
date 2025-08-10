<script lang="ts">
  import type { PageProps } from './$types';

  import { resolve } from '$app/paths';
  import { page } from '$app/state';

  import Breadcrumb from '$lib/breadcrumb.svelte';
  import BreadcrumbEntryHome from '$lib/breadcrumb/home.svelte';
  import BreadcrumbEntryType from '$lib/breadcrumb/type.svelte';

  import BreadcrumbEntryResource from '$lib/breadcrumb/resource.svelte';
  import ErrorCard from '$lib/error-card.svelte';

  let { params }: PageProps = $props();
</script>

<svelte:head>
  <title>{params.type}/{params.id} - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl sm:px-6 lg:px-8">
  <Breadcrumb>
    <BreadcrumbEntryHome />
    <BreadcrumbEntryType {...params} />
    <BreadcrumbEntryResource {...params} last />
  </Breadcrumb>
</main>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
  <ErrorCard>
    {#if page.status === 404}
      <a href={resolve('/[type=type]', params)} class="text-sm font-semibold text-gray-900"
        >Go to {params.type}s <span aria-hidden="true">&rarr;</span></a
      >
    {:else if page.status === 410}
      <a
        href={resolve('/[type=type]/[id=id]/_history', params)}
        class="text-sm font-semibold text-gray-900"
        >Go to History <span aria-hidden="true">&rarr;</span></a
      >
    {/if}
  </ErrorCard>
</main>
