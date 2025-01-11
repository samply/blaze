<script lang="ts">
	import type { PageData } from './$types';

	import { page } from '$app/state';

	import BreadcrumbEntryHome from '$lib/breadcrumb/home.svelte';
	import BreadcrumbEntryType from '$lib/breadcrumb/type.svelte';
	import BreadcrumbEntryHistory from '$lib/breadcrumb/history.svelte';

	import TotalCard from '$lib/total-card.svelte';
	import TotalBadge from '$lib/total-badge.svelte';
	import EntryCard from '$lib/history/entry-card.svelte';

	interface Props {
		data: PageData;
	}

	let { data }: Props = $props();
</script>

<svelte:head>
	<title>History - {page.params.type} - Blaze</title>
</svelte:head>

<header class="mx-auto max-w-7xl sm:px-6 lg:px-8">
	<nav class="flex pl-8 py-4 border-b border-gray-200" aria-label="Breadcrumb">
		<ol class="flex items-center py-0.5 space-x-4">
			<BreadcrumbEntryHome />
			<BreadcrumbEntryType />
			<BreadcrumbEntryHistory url="{page.params.type}/_history" />
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

	{#if data.bundle.fhirObjectEntry !== undefined && data.bundle.fhirObjectEntry.length > 0}
		{#each data.bundle.fhirObjectEntry as entry ((entry.fullUrl || '') + (entry.response?.etag || ''))}
			<EntryCard {entry} />
		{/each}
	{/if}
</main>
