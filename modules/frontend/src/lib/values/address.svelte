<script lang="ts">
  import type { Address } from 'fhir/r4';
  import { joinStrings } from '#lib/util.js';
  import Single from './address/single.svelte';
  import Badge from '#lib/tailwind/badge.svelte';

  interface Props {
    values: Address[];
  }

  let { values }: Props = $props();
</script>

{#if values.length > 1}
  <div class="rounded-lg ring-1 ring-gray-300 dark:ring-gray-500">
    <table class="w-full table-fixed">
      <tbody class="divide-y divide-gray-200 dark:divide-gray-600">
        {#each values as value}
          <tr>
            <td class="table-cell w-1/3 px-5 py-3 text-sm text-gray-500 dark:text-gray-400"
              >{joinStrings('/', value.use, value.type) ?? '<not-available>'}</td
            >
            <td class="table-cell px-5 py-3 text-sm text-gray-500 dark:text-gray-400">
              <Single {value} />
            </td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>
{:else if values.length === 1}
  <Single value={values[0]} />
  {#if values[0].use}
    <Badge value={values[0].use} />
  {/if}
{/if}
