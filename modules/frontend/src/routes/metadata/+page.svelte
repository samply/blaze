<script lang="ts">
	import type { Data } from './+page.js';

	import MetadataCard from './metadata-card.svelte';

	export let data: Data;
</script>

<svelte:head>
	<title>Metadata - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl sm:px-6 lg:px-8">
	<MetadataCard resource={data.capabilityStatement} />
</main>
