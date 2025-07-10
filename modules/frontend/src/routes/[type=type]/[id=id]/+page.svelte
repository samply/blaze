<script lang="ts">
  import type { PageProps } from './$types';
  import type { FhirResource } from 'fhir/r4';

  import BreadcrumbEntryHome from '$lib/breadcrumb/home.svelte';
  import BreadcrumbEntryType from '$lib/breadcrumb/type.svelte';
  import BreadcrumbEntryResource from '$lib/breadcrumb/resource.svelte';

  import ResourceCard from '$lib/resource/resource-card.svelte';
  import HistoryButton from './history-button.svelte';
  import OperationDropdown from './operation-dropdown.svelte';

  import { title } from '$lib/resource.js';

  let { data }: PageProps = $props();

  let resource = $derived(data.resource.object as FhirResource);
</script>

<svelte:head>
  <title>{title(resource)} - Blaze</title>
</svelte:head>

<header class="mx-auto max-w-7xl sm:px-6 lg:px-8">
  <div class="flex gap-2 pl-8 pr-4 sm:pr-6 py-3.5 border-b border-gray-200">
    <nav class="flex flex-auto" aria-label="Breadcrumb">
      <ol class="flex items-center py-0.5 space-x-4">
        <BreadcrumbEntryHome />
        <BreadcrumbEntryType />
        <BreadcrumbEntryResource {resource} last />
      </ol>
    </nav>
    <OperationDropdown />
    <HistoryButton />
  </div>
</header>

{#if data.resource}
  <main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
    <ResourceCard resource={data.resource}>
      {#snippet header()}
        <div></div>
      {/snippet}
    </ResourceCard>
  </main>
{/if}
