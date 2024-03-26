<script lang="ts">
	import type { PageData } from './$types';

	import { base } from '$app/paths';
	import ListItem from './list-item.svelte';

	export let data: PageData;
</script>

<svelte:head>
	<title>Jobs - Admin - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
	<a
		class="w-20 rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
		href="{base}/__admin/jobs/new"
	>
		New Job
	</a>
	<ul role="list" class="divide-y divide-gray-100">
		{#if data.ready.entry && data.ready.entry.length > 0}
			{#each data.ready.entry as entry}
				{#if entry.resource}
					<ListItem job={entry.resource} />
				{/if}
			{/each}
		{/if}
	</ul>
	<ul role="list" class="divide-y divide-gray-100">
		{#if data.running.entry && data.running.entry.length > 0}
			{#each data.running.entry as entry}
				{#if entry.resource}
					<ListItem job={entry.resource} />
				{/if}
			{/each}
		{/if}
	</ul>
</main>
