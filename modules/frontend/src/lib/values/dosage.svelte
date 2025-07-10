<script lang="ts">
  import type { FhirObject } from '$lib/resource/resource-card.js';
  import type { Dosage } from 'fhir/r4';

  interface Props {
    values: FhirObject[];
  }

  let { values }: Props = $props();

  let dosages = $derived(values.map((v) => v.object) as Dosage[]);
</script>

<div class="ring-1 ring-gray-300 rounded-lg">
  <table class="table-fixed w-full">
    <tbody class="divide-y divide-gray-200">
      {#each dosages as dosage}
        <tr>
          <td class="px-5 py-3 text-sm text-gray-500 table-cell w-1/3"
            >{dosage.sequence ?? '<not-available>'}</td
          >
          <td class="px-5 py-3 text-sm text-gray-500 table-cell"
            >{dosage.text ?? '<not-available>'}</td
          >
        </tr>
      {/each}
    </tbody>
  </table>
</div>
