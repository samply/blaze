<script lang="ts">
  import type { FhirObject } from '$lib/resource/resource-card.js';
  import type { Dosage } from 'fhir/r4';

  interface Props {
    values: FhirObject[];
  }

  let { values }: Props = $props();

  let dosages = $derived(values.map((v) => v.object) as Dosage[]);
</script>

<div class="rounded-lg ring-1 ring-gray-300 dark:ring-gray-500">
  <table class="w-full table-fixed">
    <tbody class="divide-y divide-gray-200 dark:divide-gray-600">
      {#each dosages as dosage}
        <tr>
          <td class="table-cell w-1/3 px-5 py-3 text-sm text-gray-500 dark:text-gray-400"
            >{dosage.sequence ?? '<not-available>'}</td
          >
          <td class="table-cell px-5 py-3 text-sm text-gray-500 dark:text-gray-400"
            >{dosage.text ?? '<not-available>'}</td
          >
        </tr>
      {/each}
    </tbody>
  </table>
</div>
