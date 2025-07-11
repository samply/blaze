<script lang="ts">
  import type { Address } from 'fhir/r4';
  import type { FhirObject } from '$lib/resource/resource-card.js';
  import { joinStrings } from '$lib/util.js';
  import Single from './address/single.svelte';
  import Badge from '$lib/tailwind/badge.svelte';

  interface Props {
    values: FhirObject<Address>[];
  }

  let { values }: Props = $props();
</script>

{#if values.length > 1}
  <div class="ring-1 ring-gray-300 rounded-lg">
    <table class="table-fixed w-full">
      <tbody class="divide-y divide-gray-200">
        {#each values as value}
          <tr>
            <td class="px-5 py-3 text-sm text-gray-500 table-cell w-1/3"
              >{joinStrings('/', value.object.use, value.object.type) ?? '<not-available>'}</td
            >
            <td class="px-5 py-3 text-sm text-gray-500 table-cell">
              <Single value={value.object} />
            </td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>
{:else if values.length === 1}
  <Single value={values[0].object} />
  {#if values[0].object.use}
    <Badge value={values[0].object.use} />
  {/if}
{/if}
