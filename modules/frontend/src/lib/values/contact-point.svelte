<script lang="ts">
  import type { FhirObject } from '$lib/resource/resource-card.js';
  import type { ContactPoint } from 'fhir/r4';
  import { joinStrings } from '$lib/util.js';
  import GrayBadge from './util/gray-badge.svelte';

  interface Props {
    values: FhirObject[];
  }

  let { values }: Props = $props();

  let contactPoints = $derived(values.map((v) => v.object) as ContactPoint[]);
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
    <GrayBadge value={contactPoints[0].system} />
  {/if}
  {#if contactPoints[0].use}
    <GrayBadge value={contactPoints[0].use} />
  {/if}
{/if}
