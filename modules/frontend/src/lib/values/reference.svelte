<script lang="ts">
  import type { FhirObject } from '$lib/resource/resource-card.js';
  import type { Reference } from 'fhir/r4';

  import { resolve } from '$app/paths';

  interface Props {
    values: FhirObject[];
  }

  let { values }: Props = $props();

  const max = 10;

  let references = $derived(values.slice(0, max).map((v) => v.object) as Reference[]);

  function toRouteParams(reference: string) {
    const [type, id] = reference.split('/');
    return { type: type, id: id };
  }
</script>

{#each references as reference}
  <p>
    {#if reference.reference}
      {#if /[A-Z]([A-Za-z0-9_]){0,254}\/[A-Za-z0-9\-.]{1,64}/.test(reference.reference)}
        <a
          href={resolve('/[type=type]/[id=id]', toRouteParams(reference.reference))}
          class="font-medium text-indigo-600 dark:text-indigo-300 hover:text-indigo-500 hover:dark:text-indigo-400"
          >{#if reference.display}{reference.display}{:else}{reference.reference}{/if}</a
        >
      {:else if reference.display}
        {reference.display}
      {:else}
        {reference.reference}
      {/if}
    {:else if reference.display}
      {reference.display}
    {/if}
  </p>
{/each}
{#if values.length > max}
  <p>...</p>
{/if}
