<script lang="ts">
	import type { Data } from './+page.js';

	import Table from '$lib/tailwind/table/table.svelte';
	import ResourceRow from './resource-row.svelte';

	export let data: Data;
</script>

<svelte:head>
	<title>Home - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
	<Table>
		<div slot="caption">
			<h1 class="text-base font-semibold leading-6 text-gray-900">Resource Types</h1>
			<p class="mt-2 text-sm text-gray-700">
				A list of all resource types available in this Blaze instance with their total count.
			</p>
		</div>

		<tr slot="head">
			<th scope="col" class="py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 sm:pl-0"
				>Name</th
			>
			<th
				scope="col"
				class="py-3.5 pl-4 pr-3 text-right text-sm font-semibold text-gray-900 sm:pl-0"
				>Total Count</th
			>
		</tr>

		{#each data.resourceTypeTotals as resourceTypeTotal}
			<ResourceRow {...resourceTypeTotal} />
		{/each}
	</Table>
</main>
