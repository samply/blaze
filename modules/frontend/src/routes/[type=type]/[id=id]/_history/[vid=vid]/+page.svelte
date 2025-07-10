<script lang="ts">
  import type { PageProps } from './$types';

  import { page } from '$app/state';

  import Breadcrumb from '$lib/breadcrumb.svelte';
  import BreadcrumbEntryHome from '$lib/breadcrumb/home.svelte';
  import BreadcrumbEntryType from '$lib/breadcrumb/type.svelte';
  import BreadcrumbEntryResource from '$lib/breadcrumb/resource.svelte';
  import BreadcrumbEntryVersion from '$lib/breadcrumb/version.svelte';
  import ResourceCard from '$lib/resource/resource-card.svelte';

  let { data }: PageProps = $props();
</script>

<svelte:head>
  <title>Version ${page.params.vid} - {page.params.type}/{page.params.id} - Blaze</title>
</svelte:head>

<header class="mx-auto max-w-7xl sm:px-6 lg:px-8">
  <Breadcrumb>
    <BreadcrumbEntryHome />
    <BreadcrumbEntryType />
    <BreadcrumbEntryResource />
    <BreadcrumbEntryVersion />
  </Breadcrumb>
</header>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
  <ResourceCard resource={data.resource} />
</main>
