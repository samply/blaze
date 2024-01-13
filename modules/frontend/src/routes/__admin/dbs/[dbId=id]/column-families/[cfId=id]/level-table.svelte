<script lang="ts">
	import Table from '../../../../../../tailwind/table/table.svelte';
	import LevelRow from './level-row.svelte';
	import type { Level } from './+page.js';

	export let levels: Level[];
</script>

<Table clazz="mt-4">
	<h4 slot="caption" class="text-base font-semibold leading-6 text-gray-900">Levels</h4>
	<tr slot="head">
		<th scope="col" class="py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 sm:pl-0"
			>Level</th
		>
		<th scope="col" class="px-3 py-3.5 text-right text-sm font-semibold text-gray-900"># Files</th>
		<th scope="col" class="px-3 py-3.5 text-right text-sm font-semibold text-gray-900">Size</th>
	</tr>

	{#each levels as level}
		<LevelRow {...level} />
	{/each}
</Table>
