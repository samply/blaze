<script lang="ts">
	import type { PageData } from './$types';

	import { page } from '$app/stores';

	export let data: PageData;
</script>

<svelte:head>
	<title>Job/{$page.params.id} - Admin - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
	{data.job.id}
</main>
