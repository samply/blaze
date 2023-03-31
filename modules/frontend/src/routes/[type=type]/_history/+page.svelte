<script lang="ts">
	import { page } from '$app/stores';

	import BreadcrumbEntryHome from '../../breadcrumb-entry-home.svelte';
	import BreadcrumbEntryType from '../breadcrumb-entry-type.svelte';
	import BreadcrumbEntryHistory from './breadcrumb-entry-history.svelte';
	import TotalCard from '../../../total-card.svelte';
	import TotalBadge from '../../../total-badge.svelte';
	import EntryCard from '../../../history/entry-card.svelte';

	export let data;
</script>

<svelte:head>
	<title>History - {$page.params.type} - Blaze</title>
</svelte:head>

<header class="fixed w-full bg-white shadow">
	<div class="mx-auto max-w-7xl px-4 py-4 sm:px-6 lg:px-8">
		<nav class="flex" aria-label="Breadcrumb">
			<ol class="flex items-center py-0.5 space-x-4">
				<BreadcrumbEntryHome />
				<BreadcrumbEntryType />
				<BreadcrumbEntryHistory />
			</ol>
		</nav>
	</div>
</header>

<main class="pt-14">
	<div class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8 flex flex-col gap-4">
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
	</div>
</main>
