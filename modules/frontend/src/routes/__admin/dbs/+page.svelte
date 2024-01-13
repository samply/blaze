<script lang="ts">
	import type { Data } from './+page.js';

	import DatabaseCard from './database-card.svelte';

	export let data: Data;
</script>

<svelte:head>
	<title>Databases - Admin - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
	<ul role="list" class="grid grid-cols-1 gap-x-6 gap-y-8 lg:grid-cols-3 xl:gap-x-8">
		{#each data.databases as dbStats}
			<DatabaseCard stats={dbStats} />
		{/each}
	</ul>
</main>
