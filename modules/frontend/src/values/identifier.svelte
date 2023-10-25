<script lang="ts">
	import type { FhirObject } from '../resource/resource-card.js';
	import type { Identifier } from '../fhir.js';
	import { sortByProperty } from '../util.js';

	function compactType(value: Identifier): string | undefined {
		return value.type === undefined ? undefined : value.type.text;
	}

	export let values: FhirObject[];

	$: compactValues = values
		.map((v) => ({ type: compactType(v.object), value: v.object.value }))
		.sort(sortByProperty('type'));
</script>

<div class="ring-1 ring-gray-300 rounded-lg">
	<table class="table-fixed w-full">
		<tbody class="divide-y divide-gray-200">
			{#each compactValues as value}
				<tr>
					<td class="px-5 py-3 text-sm text-gray-500 table-cell w-1/3"
						>{value.type ?? '<not-available>'}</td
					>
					<td class="px-5 py-3 text-sm text-gray-500 table-cell">{value.value}</td>
				</tr>
			{/each}
		</tbody>
	</table>
</div>
