<script lang="ts">
  import type { PageProps } from './$types';

  import Breadcrumb from '$lib/breadcrumb.svelte';
  import BreadcrumbEntryHome from '$lib/breadcrumb/home.svelte';
  import BreadcrumbEntryType from '$lib/breadcrumb/type.svelte';
  import BreadcrumbEntryResource from '$lib/breadcrumb/resource.svelte';
  import BreadcrumbEntry from '$lib/breadcrumb/entry.svelte';
  import Section from '$lib/tailwind/form/section.svelte';
  import SubmitButton from '$lib/tailwind/form/button-submit.svelte';
  import EntryCard from '$lib/history/entry-card.svelte';
  import NoResultsCard from '../../no-results-card.svelte';

  import { title } from '$lib/resource.js';
  import type { FhirResource } from 'fhir/r4';

  let { data }: PageProps = $props();

  let resource = $derived(data.resource.object as FhirResource);
</script>

<svelte:head>
  <title>$graph - {title(resource)} - Blaze</title>
</svelte:head>

<header class="mx-auto max-w-7xl sm:px-6 lg:px-8">
  <Breadcrumb>
    <BreadcrumbEntryHome />
    <BreadcrumbEntryType />
    <BreadcrumbEntryResource {resource} />
    <BreadcrumbEntry>
      <span class="ml-4 text-sm font-medium text-gray-500">$graph</span>
    </BreadcrumbEntry>
  </Breadcrumb>
</header>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8 flex flex-col gap-4">
  {#if data.graphDefinitions}
    <form class="mt-4">
      <div class="space-y-12 sm:space-y-16">
        <Section name="Parameters">
          <div class="sm:grid sm:grid-cols-3 sm:items-start sm:gap-4 sm:py-6">
            <label for="definition" class="block text-sm/6 font-medium text-gray-900 sm:pt-1.5"
              >Definition</label
            >
            <div class="mt-2 sm:col-span-2 sm:mt-0">
              <select
                id="definition"
                name="graph"
                class="col-start-1 row-start-1 w-full appearance-none rounded-md bg-white py-1.5 pl-3 pr-8 text-base text-gray-900 outline outline-1 -outline-offset-1 outline-gray-300 focus:outline focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-600 sm:text-sm/6"
              >
                <option value="__select">please select</option>
                {#each data.graphDefinitions as graphDefinition}
                  <option
                    value={graphDefinition.url}
                    selected={data.selectedGraphDefinitionUrl === graphDefinition.url}
                    >{graphDefinition.name}</option
                  >
                {/each}
              </select>
            </div>
          </div>
        </Section>
      </div>
      <div class="mt-6 flex items-center justify-end gap-x-6">
        <SubmitButton name="Submit" />
      </div>
    </form>
  {:else}
    <div class="overflow-hidden text-center py-10 text-3xl text-gray-700">
      No GraphDefinitions available
    </div>
  {/if}

  {#if data.graph}
    {#if data.graph.fhirObjectEntry !== undefined && data.graph.fhirObjectEntry.length > 0}
      {#each data.graph.fhirObjectEntry as entry (entry.fullUrl)}
        <EntryCard {entry} />
      {/each}
    {:else if data.graph.total === undefined}
      <NoResultsCard />
    {/if}
  {/if}
</main>
