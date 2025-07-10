<script lang="ts">
  import type { FhirObject } from '$lib/resource/resource-card.js';
  import type { HumanName } from 'fhir/r4';
  import GrayBadge from './util/gray-badge.svelte';

  interface Props {
    values: FhirObject[];
  }

  let { values }: Props = $props();

  function display(value: HumanName): string {
    return (
      value.text ??
      [...(value.prefix ?? []), ...(value.given ?? []), value.family, ...(value.suffix ?? [])].join(
        ' '
      )
    );
  }

  let humanNames = $derived(values.map((v) => v.object) as HumanName[]);
</script>

{#if humanNames.length > 1}
  <div class="ring-1 ring-gray-300 rounded-lg">
    <table class="table-fixed w-full">
      <tbody class="divide-y divide-gray-200">
        {#each humanNames as value}
          <tr>
            <td class="px-5 py-3 text-sm text-gray-500 table-cell w-1/3"
              >{value.use ?? '<not-available>'}</td
            >
            <td class="px-5 py-3 text-sm text-gray-500 table-cell">{display(value)}</td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>
{:else if humanNames.length === 1}
  {display(humanNames[0])}
  {#if humanNames[0].use}
    <GrayBadge value={humanNames[0].use} />
  {/if}
{/if}
