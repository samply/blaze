<script lang="ts">
	import Table from '../../../../tailwind/table/table.svelte';
	import ColumnFamilyRow from './column-family-row.svelte';
	import type { ColumnFamilyData } from './+page.js';

	export let columnFamilies: ColumnFamilyData[];
</script>

<Table>
	<h4 slot="caption" class="text-base font-semibold leading-6 text-gray-900">Column Families</h4>
	<tr slot="head">
		<th scope="col" class="py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 sm:pl-0"
			>Name</th
		>
		<th
			scope="col"
			class="hidden px-3 py-3.5 text-right text-sm font-semibold text-gray-900 sm:table-cell"
			># Keys</th
		>
		<th scope="col" class="px-3 py-3.5 text-right text-sm font-semibold text-gray-900">File Size</th
		>
		<th
			scope="col"
			class="hidden px-3 py-3.5 text-right text-sm font-semibold text-gray-900 lg:table-cell"
			>Memtable Size</th
		>
	</tr>

	{#each columnFamilies as columnFamily}
		<ColumnFamilyRow {...columnFamily} />
	{/each}
</Table>
