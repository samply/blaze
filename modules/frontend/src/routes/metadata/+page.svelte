<script lang="ts">
	import type { PageData } from './$types';

	import MetadataCard from './metadata-card.svelte';

	interface Props {
		data: PageData;
	}

	let { data }: Props = $props();
</script>

<svelte:head>
	<title>Metadata - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl sm:px-6 lg:px-8">
	<MetadataCard resource={data.capabilityStatement} />
</main>
