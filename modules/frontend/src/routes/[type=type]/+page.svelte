<script lang="ts">
	import { onMount } from 'svelte';
	import { page } from '$app/stores';
	import { fade, slide } from 'svelte/transition';

	import BreadcrumbEntryHome from '../breadcrumb-entry-home.svelte';
	import BreadcrumbEntryType from './breadcrumb-entry-type.svelte';
	import SearchForm from './search-form.svelte';
	import TotalCard from '../../total-card.svelte';
	import TotalBadge from '../../total-badge.svelte';
	import DurationBadge from '../../duration-badge.svelte';
	import EntryCard from './entry-card.svelte';
	import NoResultsCard from './no-results-card.svelte';
	import ErrorCard from './../error-card.svelte';
	import HistoryButton from './history-button.svelte';

	export let data;

	let duration = 0;

	onMount(() => {
		const interval = setInterval(() => {
			duration = Date.now() - data.streamed.start;
		}, 100);
		return () => clearInterval(interval);
	});
</script>

<svelte:head>
	<title>{$page.params.type} - Blaze</title>
</svelte:head>

<header class="fixed w-full bg-white shadow">
	<div class="mx-auto max-w-7xl px-4 py-4 sm:px-6 lg:px-8">
		<nav class="flex" aria-label="Breadcrumb">
			<ol class="flex items-center py-0.5 space-x-4">
				<BreadcrumbEntryHome />
				<BreadcrumbEntryType />
			</ol>
		</nav>
	</div>
</header>

<main class="pt-14">
	<div class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8 flex flex-col gap-4">
		<SearchForm capabilityStatement={data.capabilityStatement} />
		{#await data.streamed.bundle}
			{#if duration > 300}
				<div
					in:fade={{ duration: 200 }}
					out:slide={{ duration: 200 }}
					class="bg-white shadow sm:rounded-lg text-center px-4 py-5 sm:px-6 text-gray-700"
				>
					<code>
						loading...
						{(duration / 1000).toLocaleString(undefined, {
							minimumFractionDigits: 1,
							maximumFractionDigits: 1
						})}
						s
					</code>
				</div>
			{/if}
		{:then bundle}
			<TotalCard {bundle}>
				<HistoryButton />
				<p class="py-1.5 ml-2">
					{#if bundle.total !== undefined}
						<TotalBadge total={bundle.total} />
					{/if}
				</p>
				<p class="flex-grow py-1.5">
					<DurationBadge duration={bundle.duration} />
				</p>
			</TotalCard>

			{#if bundle.entry && bundle.entry.length > 0}
				{#each bundle.entry as entry (entry.fullUrl)}
					<EntryCard {entry} />
				{/each}
			{:else if bundle.total === undefined}
				<NoResultsCard />
			{/if}
		{:catch error}
			<ErrorCard status={error.status} error={error.body} />
		{/await}
	</div>
</main>
