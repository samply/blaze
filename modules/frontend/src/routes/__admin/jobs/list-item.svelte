<script lang="ts">
	import { base } from '$app/paths';
	import { coding } from '$lib/fhir';
	import DateTime from '$lib/values/date-time.svelte';
	import type { Task } from 'fhir/r4';

	let jobTypeUrl = 'https://samply.github.io/blaze/fhir/CodeSystem/JobType';

	function title(job: Task): string {
		console.log(job);
		if (job.code) {
			let jobTypeCoding = coding(job.code, jobTypeUrl);
			return jobTypeCoding?.display || jobTypeCoding?.code || 'Unknown';
		} else {
			return 'Unknown';
		}
	}

	export let job: Task;
</script>

<li class="flex justify-between gap-x-6 py-5">
	<div class="flex min-w-0 gap-x-4">
		<div class="min-w-0 flex-auto">
			<a class="text-sm font-semibold leading-6 text-gray-900" href="{base}/__admin/jobs/{job.id}"
				>{title(job)}</a
			>
			{#if job.meta?.lastUpdated}
				<p class="mt-1 truncate text-xs leading-5 text-gray-500">
					Last seen
					<DateTime value={job.meta?.lastUpdated} />
				</p>
			{/if}
		</div>
	</div>
</li>
