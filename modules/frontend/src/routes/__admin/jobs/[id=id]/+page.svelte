<script lang="ts">
	import type { PageData } from './$types';
	import type { Task } from 'fhir/r4';

	import { afterUpdate, onDestroy } from 'svelte';
	import { invalidateAll } from '$app/navigation';
	import { number, type, input, output } from '$lib/jobs';
	import humanizeDuration from 'humanize-duration';
	import prettyNum from '$lib/pretty-num.js';

	import DescriptionList from '$lib/tailwind/description/left-aligned/list.svelte';
	import Row from '$lib/tailwind/description/left-aligned/row.svelte';
	import Status from '$lib/jobs/status.svelte';
	import DateTime from '$lib/values/date-time.svelte';

	const reIndexJobParameterUrl =
		'https://samply.github.io/blaze/fhir/CodeSystem/ReIndexJobParameter';
	const jobOutputUrl = 'https://samply.github.io/blaze/fhir/CodeSystem/JobOutput';
	const reIndexJobOutputUrl = 'https://samply.github.io/blaze/fhir/CodeSystem/ReIndexJobOutput';

	function resourceCounts(job: Task, code: string) {
		const total = output(job, reIndexJobOutputUrl, code)?.valueUnsignedInt;
		return total !== undefined ? prettyNum(total) : undefined;
	}

	function processingDuration(job: Task) {
		const duration = output(job, reIndexJobOutputUrl, 'processing-duration')?.valueQuantity?.value;
		return duration !== undefined ? humanizeDuration(duration * 1000, { round: true }) : undefined;
	}

	export let data: PageData;

	let timeout: ReturnType<typeof setTimeout>;

	// reload page data every 10 seconds if the job is still in progress
	afterUpdate(() => {
		if (data.job.status === 'in-progress') {
			timeout = setTimeout(() => {
				invalidateAll();
			}, 10000);
		}
	});

	onDestroy(() => {
		if (timeout) {
			clearTimeout(timeout);
		}
	});
</script>

<svelte:head>
	<title>Job #{number(data.job)} - Admin - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
	<DescriptionList>
		<svelte:fragment slot="title">Job #{number(data.job)}</svelte:fragment>
		<svelte:fragment slot="description">
			Last Updated
			{#if data.job.meta?.lastUpdated}
				<DateTime value={data.job.meta?.lastUpdated} />
			{:else}
				Unknown
			{/if}
		</svelte:fragment>
		<Row title="Status">
			<Status job={data.job} />
		</Row>
		<Row title="Type">
			{type(data.job)}
		</Row>
		{#if data.job.authoredOn}
			<Row title="Created">
				<DateTime value={data.job.authoredOn} />
			</Row>
		{/if}
		<Row title="Search Param URL">
			{input(data.job, reIndexJobParameterUrl, 'search-param-url')?.valueCanonical}
		</Row>
		{#if data.job.status === 'failed'}
			<Row title="Error">
				{output(data.job, jobOutputUrl, 'error')?.valueString}
			</Row>
		{:else if data.job.status !== 'ready'}
			<Row title="Total Resources">
				{resourceCounts(data.job, 'total-resources')}
			</Row>
			<Row title="Resources Processed">
				{resourceCounts(data.job, 'resources-processed')}
			</Row>
			<Row title="Processing Duration">
				{processingDuration(data.job)}
			</Row>
		{/if}
	</DescriptionList>
</main>
