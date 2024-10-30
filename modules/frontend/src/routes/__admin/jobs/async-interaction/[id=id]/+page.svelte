<script lang="ts">
	import type { PageData } from './$types';

	import { afterUpdate, onDestroy } from 'svelte';
	import { invalidateAll } from '$app/navigation';
	import Status from '$lib/jobs/status.svelte';
	import DescriptionList from '$lib/tailwind/description/left-aligned/list.svelte';
	import DateTime from '$lib/values/date-time.svelte';
	import Row from '$lib/tailwind/description/left-aligned/row.svelte';
	import humanizeDuration from 'humanize-duration';

	export let data: PageData;

	let timeout: ReturnType<typeof setTimeout>;

	// reload page data every 5 seconds if the job is still in progress
	afterUpdate(() => {
		if (data.job.status === 'ready' || data.job.status === 'in-progress') {
			timeout = setTimeout(() => {
				invalidateAll();
			}, 5000);
		}
	});

	onDestroy(() => {
		if (timeout) {
			clearTimeout(timeout);
		}
	});
</script>

<svelte:head>
	<title>Job #{data.job.number} - Admin - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
	<DescriptionList>
		<svelte:fragment slot="title">Job #{data.job.number}</svelte:fragment>
		<svelte:fragment slot="description">
			Last Updated
			<DateTime value={data.job.lastUpdated} />
		</svelte:fragment>
		<Row title="Status">
			<Status job={data.job} />
		</Row>
		<Row title="Type">Asynchronous Interaction Request</Row>
		<Row title="Created">
			<DateTime value={data.job.authoredOn} />
		</Row>
		<Row title="Request">
			{data.job.request}
		</Row>
		<Row title="Database Point in Time">
			{data.job.t}
		</Row>
		{#if data.job.responseStatus}
			<Row title="Response Status">
				{data.job.responseStatus}
			</Row>
		{/if}
		{#if data.job.processingDuration}
			<Row title="Processing Duration">
				{humanizeDuration(data.job.processingDuration * 1000)}
			</Row>
		{/if}
		{#if data.job.error}
			<Row title="Error">
				{data.job.error}
			</Row>
		{/if}
	</DescriptionList>
</main>
