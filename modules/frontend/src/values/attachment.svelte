<script lang="ts">
	import type { FhirObject } from '../resource/resource-card.js';

	export let values: FhirObject[];
</script>

{#if values[0].object.contentType == 'text/cql'}
	<pre class="flex overflow-auto text-sm"><code>{atob(values[0].object.data)}</code></pre>
{:else}
	<div class="ring-1 ring-gray-300 rounded-lg">
		<table class="table-fixed w-full">
			<tbody class="divide-y divide-gray-200">
				{#each values as value}
					<tr>
						<td class="px-5 py-3 text-sm text-gray-500 table-cell w-1/3"
							>{value.object.contentType ?? '<not-available>'}</td
						>
						<td class="px-5 py-3 text-sm text-gray-500 table-cell">
							{#if value.object.data !== undefined}
								<a
									href="data:{value.object.contentType};base64,{value.object.data}"
									download
									class="font-medium text-indigo-600 hover:text-indigo-500"
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
