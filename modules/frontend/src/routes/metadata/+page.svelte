<script lang="ts">
	import BreadcrumbEntryHome from '../breadcrumb-entry-home.svelte';
	import BreadcrumbEntryMetadata from './breadcrumb-entry-metadata.svelte';
	import MetadataCard from './metadata-card.svelte';

	export let data;
</script>

<svelte:head>
	<title>Metadata - Blaze</title>
</svelte:head>

<header class="fixed w-full bg-white shadow z-10">
	<div class="mx-auto max-w-7xl px-4 py-4 sm:px-6 lg:px-8">
		<nav class="flex" aria-label="Breadcrumb">
			<ol class="flex items-center py-0.5 space-x-4">
				<BreadcrumbEntryHome />
				<BreadcrumbEntryMetadata />
			</ol>
		</nav>
	</div>
</header>

<main class="pt-14">
	<div class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
		<MetadataCard resource={data.capabilityStatement} />
	</div>
</main>
