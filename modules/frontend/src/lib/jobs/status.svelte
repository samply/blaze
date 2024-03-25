<script lang="ts">
	import type { Task } from 'fhir/r4';
	import { output, statusReason } from '$lib/jobs';

	const reIndexJobOutputUrl = 'https://samply.github.io/blaze/fhir/CodeSystem/ReIndexJobOutput';

	function progress(job: Task) {
		const total = output(job, reIndexJobOutputUrl, 'total-resources')?.valueUnsignedInt;
		const processed = output(job, reIndexJobOutputUrl, 'resources-processed')?.valueUnsignedInt;
		return total !== undefined && total > 0 && processed !== undefined
			? Math.ceil((100 * processed) / total)
			: 0;
	}

	export let job: Task;
</script>

{#if job.status === 'completed'}
	<span
		class="inline-flex items-center rounded-md bg-green-50 px-2 py-1 text-xs font-medium text-green-700 ring-1 ring-inset ring-green-600/20"
		>completed</span
	>
{:else if job.status === 'failed'}
	<span
		class="inline-flex items-center rounded-md bg-red-50 px-2 py-1 text-xs font-medium text-red-700 ring-1 ring-inset ring-red-600/20"
		>failed</span
	>
{:else if job.status === 'in-progress'}
	<span
		class="inline-flex items-center rounded-md bg-gradient-to-r from-blue-200 to-blue-50 px-2 py-1 text-xs font-medium text-blue-700 ring-1 ring-inset ring-blue-600/20"
		style="--tw-gradient-from-position: {progress(job)}%; --tw-gradient-to-position: {progress(
			job
		) + 5}%">in-progress</span
	>
{:else if job.status === 'on-hold'}
	{#if statusReason(job) === 'paused'}
		<span
			class="inline-flex items-center rounded-md bg-blue-50 px-2 py-1 text-xs font-medium text-blue-700 ring-1 ring-inset ring-blue-600/20"
			>paused</span
		>
	{:else if statusReason(job) === 'orderly-shutdown'}
		<span
			class="inline-flex items-center rounded-md bg-yellow-50 px-2 py-1 text-xs font-medium text-yellow-700 ring-1 ring-inset ring-yellow-600/20"
			>orderly-shutdown</span
		>
	{:else}
		<span
			class="inline-flex items-center rounded-md bg-yellow-50 px-2 py-1 text-xs font-medium text-yellow-700 ring-1 ring-inset ring-yellow-600/20"
			>on-hold</span
		>
	{/if}
{:else}
	<span
		class="inline-flex items-center rounded-md bg-gray-50 px-2 py-1 text-xs font-medium text-gray-700 ring-1 ring-inset ring-gray-600/20"
		>{job.status}</span
	>
{/if}
