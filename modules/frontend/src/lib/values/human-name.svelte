<script lang="ts">
  import type { FhirObject } from '$lib/resource/resource-card.js';
  import type { HumanName } from 'fhir/r5';
  import Badge from '$lib/tailwind/badge.svelte';

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

  // See https://hl7.org/fhir/R4B/valueset-name-use.html
  type Use = 'usual' | 'official' | 'temp' | 'nickname' | 'anonymous' | 'old' | 'maiden';

  function useTitle(use: Use): string {
    switch (use) {
      case 'usual':
        return 'Normally used name';
      case 'official':
        return 'Formal/Registered/Legal name';
      case 'temp':
        return 'Temporary name, see Name.period';
      case 'nickname':
        return 'Informal/Chosen name';
      case 'anonymous':
        return 'Anonymous assigned name/alias/pseudonym';
      case 'old':
        return 'No longer in use';
      case 'maiden':
        return 'Prior-marriage name';
    }
  }
</script>

{#if humanNames.length > 1}
  <div class="ring-1 ring-gray-300 dark:ring-gray-500 rounded-lg">
    <table class="table-fixed w-full">
      <tbody class="divide-y divide-gray-200 dark:divide-gray-600">
        {#each humanNames as value}
          <tr>
            <td class="px-5 py-3 text-sm text-gray-500 dark:text-gray-400 table-cell w-1/3"
              >{value.use ?? '<not-available>'}</td
            >
            <td class="px-5 py-3 text-sm text-gray-500 dark:text-gray-400 table-cell"
              >{display(value)}</td
            >
          </tr>
        {/each}
      </tbody>
    </table>
  </div>
{:else if humanNames.length === 1}
  {display(humanNames[0])}
  {#if humanNames[0].use}
    <Badge value={humanNames[0].use} title={useTitle(humanNames[0].use)} />
  {/if}
{/if}
