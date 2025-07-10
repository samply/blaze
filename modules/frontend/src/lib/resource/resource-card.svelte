<script lang="ts">
  import type { Snippet } from 'svelte';
  import type { Element, FhirResource, Meta, Resource } from 'fhir/r4';
  import type { FhirObject } from './resource-card.js';

  import { base } from '$app/paths';
  import { page } from '$app/state';
  import { fade } from 'svelte/transition';
  import { quintIn } from 'svelte/easing';

  import { isTabActive } from '$lib/util.js';
  import { willBeRendered as willMetaBeRendered } from '$lib/values/meta.js';
  import { title } from '$lib/resource.js';

  import TabItem from '../tab-item.svelte';
  import TabItemEmbedded from '../tab-item-embedded.svelte';
  import DateTime from '$lib/values/date-time.svelte';
  import Property from './property.svelte';
  import Object from './json/object.svelte';

  interface Props {
    resource: FhirObject;
    embedded?: boolean;
    versionLink?: boolean;
    header?: Snippet;
  }

  let { resource, embedded = false, versionLink = false, header }: Props = $props();

  function hasMeta(element: Element): element is Resource & { meta: Meta } {
    return (element as Resource).meta !== undefined;
  }

  function href(resource: FhirObject) {
    const href = `${base}/${resource.type.code}/${resource.object.id}`;
    const versionId = (resource.object as Resource).meta?.versionId;
    return versionLink && versionId !== undefined ? href + `/_history/${versionId}` : href;
  }

  function title1(resource: FhirObject) {
    return title(resource.object as FhirResource);
  }

  let properties = $derived(
    resource.properties.filter(
      (p) =>
        p.name != 'resourceType' &&
        p.name != 'id' &&
        (p.type.code != 'Meta' || willMetaBeRendered((p.value as FhirObject).object as Meta)) &&
        p.type.code != 'Narrative'
    )
  );

  let selectedTab = $state('default');

  const fadeParams = { duration: 300, easing: quintIn };
</script>

<div in:fade|global={fadeParams} class="overflow-hidden">
  <div class="border-b border-gray-200 px-8">
    <nav class="-mb-px flex space-x-8" aria-label="Tabs">
      {#if embedded}
        <TabItemEmbedded name="default" label="Normal" bind:selected={selectedTab} />
        <TabItemEmbedded name="json" label="Json" bind:selected={selectedTab} />
      {:else}
        <TabItem name="default" label="Normal" />
        <TabItem name="json" label="Json" />
      {/if}
    </nav>
  </div>
  {#if embedded ? selectedTab === 'default' : isTabActive(page.url, 'default')}
    <div in:fade|global={fadeParams} class="px-4 py-5 sm:px-6">
      <div class="flex">
        <h3 class="flex-grow text-base font-semibold leading-6 text-gray-900">
          <a href={href(resource)}>{title1(resource)}</a>
        </h3>
        {@render header?.()}
      </div>
      {#if hasMeta(resource.object) && resource.object.meta.lastUpdated !== undefined}
        <p class="mt-1 max-w-2xl text-sm text-gray-500">
          Last Updated&nbsp;<DateTime value={resource.object.meta.lastUpdated} />
        </p>
      {/if}
    </div>
    <div in:fade|global={fadeParams} class="border-t border-gray-200 px-4 py-5 sm:p-0">
      <dl class="sm:divide-y sm:divide-gray-200" role="list">
        {#each properties as property (property.name)}
          <Property {property} />
        {/each}
      </dl>
    </div>
  {:else}
    <pre in:fade|global={fadeParams} class="flex overflow-auto text-sm"><code class="p-4"
        ><Object object={resource} /></code
      ></pre>
  {/if}
</div>
