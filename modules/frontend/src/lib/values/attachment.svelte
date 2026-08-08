<script lang="ts">
  import type { Attachment } from 'fhir/r4';
  import type { FhirObject } from '$lib/resource/resource-card.js';

  interface Props {
    values: FhirObject<Attachment>[];
  }

  let { values }: Props = $props();
</script>

{#if values[0].object.contentType === 'text/cql' && values[0].object.data !== undefined}
  <pre class="flex overflow-auto text-sm"><code>{atob(values[0].object.data)}</code></pre>
{:else}
  <div class="rounded-lg ring-1 ring-gray-300 dark:ring-gray-500">
    <table class="w-full table-fixed">
      <tbody class="divide-y divide-gray-200 dark:divide-gray-600">
        {#each values as value}
          <tr>
            <td class="table-cell w-1/3 px-5 py-3 text-sm text-gray-500 dark:text-gray-400"
              >{value.object.contentType ?? '<not-available>'}</td
            >
            <td class="table-cell px-5 py-3 text-sm text-gray-500 dark:text-gray-400">
              {#if value.object.data !== undefined}
                <a
                  href="data:{value.object.contentType};base64,{value.object.data}"
                  download
                  class="font-medium text-indigo-600 hover:text-indigo-500 dark:text-indigo-300 hover:dark:text-indigo-400"
                  >{atob(value.object.data).length} bytes</a
                > (download)
              {/if}
            </td>
          </tr>
        {/each}
      </tbody>
    </table>
  </div>
{/if}
