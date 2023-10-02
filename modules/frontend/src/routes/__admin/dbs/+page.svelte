<script lang="ts">
	import DatabaseDescription from './database-description.svelte';

	export let data;
</script>

<svelte:head>
	<title>Databases - Admin - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
	{#each data.databases as dbStats}
		<DatabaseDescription stats={dbStats} />
	{/each}
</main>
