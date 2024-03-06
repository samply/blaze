<script lang="ts">
	import type { Data } from './+layout.js';

	import Table from '$lib/tailwind/table/table.svelte';
	import SettingRow from './setting-row.svelte';
	import FeatureRow from './feature-row.svelte';

	export let data: Data;
</script>

<svelte:head>
	<title>Overview - Admin - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
	<Table>
		<div slot="caption">
			<h1 class="text-base font-semibold leading-6 text-gray-900">Settings</h1>
			<p class="mt-2 text-sm text-gray-700">A list of all environment variables used by Blaze</p>
		</div>

		<tr slot="head">
			<th scope="col" class="py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 sm:pl-0"
				>Name</th
			>
			<th scope="col" class="py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 sm:pl-0"
				>Value</th
			>
			<th scope="col" class="py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 sm:pl-0"
				>Default</th
			>
		</tr>

		{#each data.settings as setting}
			<SettingRow {...setting} />
		{/each}
	</Table>
	<Table clazz="mt-8">
		<div slot="caption">
			<h1 class="text-base font-semibold leading-6 text-gray-900">Features</h1>
			<p class="mt-2 text-sm text-gray-700">Status of optional features</p>
		</div>

		<tr slot="head">
			<th scope="col" class="py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 sm:pl-0"
				>Name</th
			>
			<th scope="col" class="py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 sm:pl-0"
				>Toggle</th
			>
			<th scope="col" class="py-3.5 pl-4 pr-3 text-left text-sm font-semibold text-gray-900 sm:pl-0"
				>Enabled</th
			>
		</tr>

		{#each data.features as feature}
			<FeatureRow {...feature} />
		{/each}
	</Table>
</main>
