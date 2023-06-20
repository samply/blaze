<script lang="ts">
	import BreadcrumbEntryHome from '../../../breadcrumb-entry-home.svelte';
	import BreadcrumbEntryType from '../../breadcrumb-entry-type.svelte';
	import BreadcrumbEntryResource from '../breadcrumb-entry-resource.svelte';
	import BreadcrumbEntryHistory from './breadcrumb-entry-history.svelte';
	import EntryCard from '../../../../history/entry-card.svelte';
	import { page } from '$app/stores';

	export let data;
</script>

<svelte:head>
	<title>History - {$page.params.type}/$page.params.id} - Blaze</title>
</svelte:head>

<header class="fixed w-full bg-white shadow">
	<div class="mx-auto max-w-7xl px-4 py-4 sm:px-6 lg:px-8">
		<nav class="flex" aria-label="Breadcrumb">
			<ol class="flex items-center py-0.5 space-x-4">
				<BreadcrumbEntryHome />
				<BreadcrumbEntryType />
				<BreadcrumbEntryResource />
				<BreadcrumbEntryHistory />
			</ol>
		</nav>
	</div>
</header>

<main class="pt-14">
	<div class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8 flex flex-col gap-4">
		{#if data.bundle.entry}
			{#each data.bundle.entry as entry (entry.fullUrl + entry.response.etag)}
				<EntryCard {entry} />
			{/each}
		{/if}
	</div>
</main>
