<script lang="ts">
  import type { FhirObject } from '$lib/resource/resource-card.js';
  import type { ContactPoint } from 'fhir/r4';
  import { joinStrings } from '$lib/util.js';
  import Badge from '$lib/tailwind/badge.svelte';

  interface Props {
    values: FhirObject[];
  }

  let { values }: Props = $props();

  let contactPoints = $derived(values.map((v) => v.object) as ContactPoint[]);

  // See https://www.hl7.org/fhir/R4B/valueset-contact-point-use.html
  type Use = 'home' | 'work' | 'temp' | 'old' | 'mobile';

  function useTitle(use: Use): string {
    switch (use) {
      case 'home':
        return 'Home contact point';
      case 'work':
        return 'Office contact point';
      case 'temp':
        return 'Temporary contact point';
      case 'old':
        return 'No longer in use (or was never correct)';
      case 'mobile':
        return 'Mobile contact point';
    }
  }
</script>

{#if contactPoints.length > 1}
  <div class="ring-1 ring-gray-300 rounded-lg">
    <table class="table-fixed w-full">
      <tbody class="divide-y divide-gray-200">
        {#each contactPoints as value}
          <tr>
            <td class="px-5 py-3 text-sm text-gray-500 table-cell w-1/3"
              >{joinStrings('/', value.use, value.system) ?? '<not-available>'}</td
            >
            <td class="px-5 py-3 text-sm text-gray-500 table-cell">{value.value}</td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>
{:else if contactPoints.length === 1}
  {contactPoints[0].value}
  {#if contactPoints[0].system}
    <Badge value={contactPoints[0].system} title="Form of communication" />
  {/if}
  {#if contactPoints[0].use}
    <Badge value={contactPoints[0].use} title={useTitle(contactPoints[0].use)} />
  {/if}
{/if}
