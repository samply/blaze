<script lang="ts">
  import type { FhirResource } from 'fhir/r4';

  import { base } from '$app/paths';
  import { page } from '$app/state';

  import Entry from './entry.svelte';
  import { title } from '$lib/resource.js';

  interface Props {
    type?: string;
    resource?: FhirResource;
    last?: boolean;
  }

  let { type = page.params.type, resource, last = false }: Props = $props();

  let name = $derived(resource ? title(resource) : page.params.id);
</script>

<Entry>
  {#if last}
    <span class="ml-4 text-sm font-medium text-gray-500">{name}</span>
  {:else}
    <a
      href="{base}/{type}/{page.params.id}"
      class="ml-4 text-sm font-medium text-gray-500 hover:text-gray-700">{name}</a
    >
  {/if}
</Entry>
