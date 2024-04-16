<script lang="ts">
	import type { Task } from 'fhir/r4';

	import { base } from '$app/paths';
	import { number, type, input, output } from '$lib/jobs';
	import Status from '$lib/jobs/status-round.svelte';
	import TimeAgo from '$lib/time-ago.svelte';
	import humanizeDuration from 'humanize-duration';

	const reIndexJobParameterUrl =
		'https://samply.github.io/blaze/fhir/CodeSystem/ReIndexJobParameter';
	const reIndexJobOutputUrl = 'https://samply.github.io/blaze/fhir/CodeSystem/ReIndexJobOutput';

	function actionsAvailable(job: Task) {
		return job.status === 'in-progress' || job.status === 'on-hold';
	}

	function processingDuration(job: Task) {
		const duration = output(job, reIndexJobOutputUrl, 'processing-duration')?.valueQuantity?.value;
		return duration !== undefined ? humanizeDuration(duration * 1000, { round: true }) : undefined;
	}

	export let job: Task;
	let actionMenuOpen = false;
</script>

<li class="flex justify-between gap-x-6 py-5">
	<div class="flex min-w-0 gap-x-4">
		<Status {job} />
		<div class="min-w-0 flex-auto">
			<p class="text-sm font-semibold leading-6 text-gray-900">
				<a href="{base}/__admin/jobs/{job.id}" class="hover:underline">{type(job)}</a>
			</p>
			<p class="mt-1 flex text-xs leading-5 text-gray-500">
				#{number(job)}
				{input(job, reIndexJobParameterUrl, 'search-param-url')?.valueCanonical}
			</p>
		</div>
	</div>
	<div class="flex shrink-0 items-center gap-x-6">
		<div class="hidden sm:flex sm:flex-col sm:items-end">
			<p class="text-xs leading-6 text-gray-500">
				<svg
					xmlns="http://www.w3.org/2000/svg"
					fill="none"
					viewBox="0 0 24 24"
					stroke-width="1"
					stroke="currentColor"
					class="inline w-4 h-4"
				>
					<path
						stroke-linecap="round"
						stroke-linejoin="round"
						d="M6.75 3v2.25M17.25 3v2.25M3 18.75V7.5a2.25 2.25 0 0 1 2.25-2.25h13.5A2.25 2.25 0 0 1 21 7.5v11.25m-18 0A2.25 2.25 0 0 0 5.25 21h13.5A2.25 2.25 0 0 0 21 18.75m-18 0v-7.5A2.25 2.25 0 0 1 5.25 9h13.5A2.25 2.25 0 0 1 21 11.25v7.5"
					/>
				</svg>

				{#if job.authoredOn}
					<TimeAgo value={job.authoredOn} />
				{:else}
					Unknown
				{/if}
			</p>
			<p class="text-xs leading-6 text-gray-500">
				<svg
					xmlns="http://www.w3.org/2000/svg"
					fill="none"
					viewBox="0 0 24 24"
					stroke-width="1"
					stroke="currentColor"
					class="inline w-4 h-4"
				>
					<path
						stroke-linecap="round"
						stroke-linejoin="round"
						d="M12 6v6h4.5m4.5 0a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z"
					/>
				</svg>

				{processingDuration(job)}
			</p>
		</div>
		<div class="relative flex-none">
			<button
				type="button"
				class="-m-2.5 block p-2.5 text-gray-500 hover:text-gray-900"
				id="options-menu-0-button"
				aria-expanded="false"
				aria-haspopup="true"
				on:click={() => (actionMenuOpen = !actionMenuOpen)}
				disabled={!actionsAvailable(job)}
			>
				<span class="sr-only">Open options</span>
				<svg class="h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
					<path
						d="M10 3a1.5 1.5 0 110 3 1.5 1.5 0 010-3zM10 8.5a1.5 1.5 0 110 3 1.5 1.5 0 010-3zM11.5 15.5a1.5 1.5 0 10-3 0 1.5 1.5 0 003 0z"
					/>
				</svg>
			</button>

			<!--
				Dropdown menu, show/hide based on menu state.

				Entering: "transition ease-out duration-100"
					From: "transform opacity-0 scale-95"
					To: "transform opacity-100 scale-100"
				Leaving: "transition ease-in duration-75"
					From: "transform opacity-100 scale-100"
					To: "transform opacity-0 scale-95"
			-->
			<div
				class="absolute flex flex-col items-stretch right-0 z-10 mt-2 w-32 origin-top-right rounded-md bg-white py-2 shadow-lg ring-1 ring-gray-900/5 focus:outline-none"
				class:hidden={!actionMenuOpen}
				role="menu"
				aria-orientation="vertical"
				aria-labelledby="options-menu-0-button"
				tabindex="-1"
			>
				{#if job.status === 'in-progress'}
					<form method="POST" action="?/pause" class="flex flex-col items-stretch">
						<input type="hidden" name="job-id" value={job.id} />
						<button
							type="submit"
							class="block px-3 py-1 text-sm leading-6 text-left text-gray-900 hover:bg-gray-50"
							role="menuitem"
							tabindex="-1"
							id="options-menu-0-item-0"
							>Pause
						</button>
					</form>
				{/if}
				{#if job.status === 'on-hold'}
					<form method="POST" action="?/resume" class="flex flex-col items-stretch">
						<input type="hidden" name="job-id" value={job.id} />
						<button
							type="submit"
							class="block px-3 py-1 text-sm leading-6 text-left text-gray-900 hover:bg-gray-50"
							role="menuitem"
							tabindex="-1"
							id="options-menu-0-item-0"
							>Resume
						</button>
					</form>
				{/if}
			</div>
		</div>
	</div>
</li>
