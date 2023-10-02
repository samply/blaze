<script lang="ts">
	import Table from '../../../tailwind/table/table.svelte';
	import BloomFilterRow from './bloom-filter-row.svelte';

	export let data;
</script>

<svelte:head>
	<title>CQL - Admin - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
	<Table>
		<div slot="caption">
			<h1 class="text-base font-semibold leading-6 text-gray-900">Bloom filters</h1>
			<p class="mt-2 text-sm text-gray-700">
				Used to improve CQL performance. Only the first 100 are shown.
			</p>
		</div>

		<tr slot="head">
			<th
				scope="col"
				class="py-3.5 pl-4 pr-3 text-right text-sm font-semibold text-gray-900 sm:pl-0">T</th
			>
			<th
				scope="col"
				class="whitespace-nowrap py-3.5 pl-4 pr-3 text-right text-sm font-semibold text-gray-900 sm:pl-0"
				># Patients</th
			>
			<th
				scope="col"
				class="whitespace-nowrap py-3.5 pl-4 pr-3 text-right text-sm font-semibold text-gray-900 sm:pl-0"
				>Mem Size</th
			>
			<th
				scope="col"
				class="whitespace-nowrap py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 sm:pl-0"
				>Expr Form</th
			>
		</tr>

		{#each data.bloomFilters as bloomFilter}
			<BloomFilterRow {...bloomFilter} />
		{/each}
	</Table>
</main>
