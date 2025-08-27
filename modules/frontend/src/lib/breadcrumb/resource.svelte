<script lang="ts">
  import type { FhirResource } from 'fhir/r5';

  import { resolve } from '$app/paths';

  import Entry from './entry.svelte';
  import { title } from '$lib/resource.js';

  interface Props {
    type: string;
    id: string;
    resource?: FhirResource;
    last?: boolean;
  }

  let { type, id, resource, last = false }: Props = $props();

  let name = $derived(resource ? title(resource) : id);
</script>

<Entry>
  {#if last}
    <span class="ml-4 text-sm font-medium text-gray-500 dark:text-gray-400">{name}</span>
  {:else}
    <a
      href={resolve('/[type=type]/[id=id]', { type: type, id: id })}
      class="ml-4 text-sm font-medium text-gray-500 dark:text-gray-400 hover:text-gray-700 hover:dark:text-gray-300"
      >{name}</a
    >
  {/if}
</Entry>
