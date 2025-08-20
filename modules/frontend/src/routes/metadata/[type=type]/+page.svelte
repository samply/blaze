<script lang="ts">
  import type { PageProps } from './$types';

  import { resolve } from '$app/paths';
  import { sortByProperty2 } from '$lib/util';

  import Breadcrumb from '$lib/breadcrumb.svelte';
  import BreadcrumbEntryHome from '$lib/breadcrumb/home.svelte';
  import BreadcrumbEntryMetadata from '$lib/breadcrumb/metadata.svelte';
  import BreadcrumbEntryType from '$lib/breadcrumb/type.svelte';
  import DescriptionList from '$lib/tailwind/description/left-aligned/list.svelte';
  import Row from '$lib/tailwind/description/left-aligned/row-5-4.svelte';
  import ExternalLink from '$lib/values/util/external-link.svelte';

  let { data, params }: PageProps = $props();

  let resource = $derived(
    data.capabilityStatement.rest
      ?.at(0)
      ?.resource?.filter((r) => r.type === params.type)
      .at(0)
  );
</script>

<svelte:head>
  <title>{params.type} - Metadata - Blaze</title>
</svelte:head>

<header class="mx-auto max-w-7xl sm:px-6 lg:px-8">
  <Breadcrumb>
    <BreadcrumbEntryHome />
    <BreadcrumbEntryMetadata />
    <BreadcrumbEntryType {...params} />
  </Breadcrumb>
</header>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8 flex flex-col gap-4">
  <div class="sm:px-6">
    <DescriptionList>
      {#snippet title()}
        {params.type}
      {/snippet}
      {#snippet description()}
        <a class="hover:text-gray-400" href="{resolve('/[type=type]', { type: params.type })}/"
          >Resources</a
        >
      {/snippet}
      <Row title="Spec">
        <ExternalLink href="https://hl7.org/fhir/R4/{params.type}.html">{params.type}</ExternalLink>
      </Row>
      <Row title="Supported Profiles">
        {#if resource?.supportedProfile !== undefined}
          <ul>
            {#each resource?.supportedProfile as profile}
              <li>
                <a
                  class="hover:text-gray-500 dark:text-gray-400"
                  href="{resolve('/StructureDefinition')}?url={profile}">{profile}</a
                >
              </li>
            {/each}
          </ul>
        {:else}
          <p>no supported profiles available</p>
        {/if}
      </Row>
      <Row title="Search Parameters">
        {#if resource?.searchParam !== undefined}
          <ul>
            {#each resource?.searchParam.sort(sortByProperty2('type', 'name')) as searchParam}
              <li>({searchParam.type}) – {searchParam.name}</li>
            {/each}
          </ul>
        {:else}
          <p>no search parameters available</p>
        {/if}
      </Row>
      <Row title="Operations">
        {#if resource?.operation !== undefined}
          <ul>
            {#each resource?.operation as operation}
              <li>{operation.name}</li>
            {/each}
          </ul>
        {:else}
          <p>no operations available</p>
        {/if}
      </Row>
    </DescriptionList>
  </div>
</main>
