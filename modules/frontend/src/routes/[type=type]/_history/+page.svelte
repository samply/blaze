<script lang="ts">
	import type { PageData } from './$types';

	import { page } from '$app/stores';

	import BreadcrumbEntryHome from '../../breadcrumb-entry-home.svelte';
	import BreadcrumbEntryType from '../breadcrumb-entry-type.svelte';
	import BreadcrumbEntryHistory from './breadcrumb-entry-history.svelte';

	import TotalCard from '../../../total-card.svelte';
	import TotalBadge from '../../../total-badge.svelte';
	import EntryCard from '../../../history/entry-card.svelte';

	export let data: PageData;
</script>

<svelte:head>
	<title>History - {$page.params.type} - Blaze</title>
</svelte:head>

<header class="mx-auto max-w-7xl sm:px-6 lg:px-8">
	<nav class="flex pl-8 py-4 border-b border-gray-200" aria-label="Breadcrumb">
		<ol class="flex items-center py-0.5 space-x-4">
			<BreadcrumbEntryHome />
			<BreadcrumbEntryType />
			<BreadcrumbEntryHistory />
		</ol>
	</nav>
</header>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8 flex flex-col gap-4">
	<TotalCard bundle={data.bundle}>
		<p class="flex-grow py-1.5 ml-2">
			{#if data.bundle.total !== undefined}
				<TotalBadge total={data.bundle.total} />
			{/if}
		</p>
	</TotalCard>

	{#if data.bundle.entry}
		{#each data.bundle.entry as entry (entry.fullUrl + entry.response.etag)}
			<EntryCard {entry} />
		{/each}
	{/if}
</main>
