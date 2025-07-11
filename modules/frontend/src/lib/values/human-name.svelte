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

  function useTitle(use: string): string {
    switch (use) {
      case 'usual':
        return 'Known as/conventional/the one you normally use.';
      case 'official':
        return 'The formal name as registered in an official (government) registry, but which name might not be commonly used. May be called "legal name".';
      case 'temp':
        return 'A temporary name. Name.period can provide more detailed information. This may also be used for temporary names assigned at birth or in emergency situations.';
      case 'nickname':
        return 'A name that is used to address the person in an informal manner, but is not part of their formal or usual name.';
      case 'anonymous':
        return "Anonymous assigned name, alias, or pseudonym (used to protect a person's identity for privacy reasons).";
      case 'old':
        return 'This name is no longer in use (or was never correct, but retained for records).';
      default:
        return "A name used prior to changing name because of marriage. This name use is for use by applications that collect and store names that were used prior to a marriage. Marriage naming customs vary greatly around the world, and are constantly changing. This term is not gender specific. The use of this term does not imply any particular history for a person's name.";
    }
  }
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
    <GrayBadge value={humanNames[0].use} title={useTitle(humanNames[0].use)} />
  {/if}
{/if}
