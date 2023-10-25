<script lang="ts">
	import type { Level } from './+page.js';
	import LevelRow from './level-row.svelte';

	export let levels: Level[];
</script>

<div class="mt-4">
	<h3 class="text-base font-semibold leading-6 text-gray-900">Levels</h3>
	<div class="mt-2 overflow-hidden shadow-sm ring-1 ring-black ring-opacity-5 sm:rounded-lg">
		<table class="min-w-full divide-y divide-gray-300">
			<thead class="bg-gray-50">
				<tr>
					<th
						scope="col"
						class="py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 sm:pl-6">Level</th
					>
					<th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900"
						># Files</th
					>
					<th scope="col" class="px-3 py-3.5 text-left text-sm font-semibold text-gray-900">Size</th
					>
				</tr>
			</thead>
			<tbody class="divide-y divide-gray-200 bg-white">
				{#each levels as level}
					<LevelRow {...level} />
				{/each}
			</tbody>
		</table>
	</div>
</div>
