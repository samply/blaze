<script lang="ts">
  import type { Identifier } from 'fhir/r4';
  import type { FhirObject } from '$lib/resource/resource-card.js';

  import { sortByProperty } from '$lib/util.js';

  function compactType(value: Identifier): string | undefined {
    return value.type?.text;
  }

  interface Props {
    values: FhirObject<Identifier>[];
  }

  let { values }: Props = $props();

  let compactValues = $derived(
    values
      .map((v) => ({ type: compactType(v.object), value: v.object.value }))
      .sort(sortByProperty('type'))
  );
</script>

<div class="rounded-lg ring-1 ring-gray-300 dark:ring-gray-500">
  <table class="w-full table-fixed">
    <tbody class="divide-y divide-gray-200 dark:divide-gray-600">
      {#each compactValues as value}
        <tr>
          <td class="table-cell w-1/3 px-5 py-3 text-sm text-gray-500 dark:text-gray-400"
            >{value.type ?? '<not-available>'}</td
          >
          <td class="table-cell px-5 py-3 text-sm text-gray-500 dark:text-gray-400"
            >{value.value}</td
          >
        </tr>
      {/each}
    </tbody>
  </table>
</div>
