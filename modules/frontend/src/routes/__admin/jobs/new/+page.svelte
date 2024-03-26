<script lang="ts">
	import { base } from '$app/paths';
	import { goto } from '$app/navigation';
	import type { Task } from 'fhir/r4';

	const display = new Map();
	display.set('re-index', '(Re)Index a Search Parameter');

	async function handleSubmit(event: { currentTarget: EventTarget & HTMLFormElement }) {
		const data = new FormData(event.currentTarget);

		console.log(data);

		const res = await fetch(`${base}/__admin/jobs`, {
			method: 'POST',
			headers: { 'Content-Type': 'application/fhir+json', Accept: 'application/fhir+json' },
			body: JSON.stringify({
				resourceType: 'Task',
				meta: {
					profile: ['https://samply.github.io/blaze/fhir/StructureDefinition/ReIndexJob']
				},
				intent: 'order',
				status: 'ready',
				code: {
					coding: [
						{
							code: data.get('type'),
							system: 'https://samply.github.io/blaze/fhir/CodeSystem/JobType',
							display: display.get(data.get('type'))
						}
					]
				},
				input: [
					{
						type: {
							coding: [
								{
									code: 'search-param-url',
									system: 'https://samply.github.io/blaze/fhir/CodeSystem/ReIndexJobParameter'
								}
							]
						},
						valueCanonical: data.get('search-param-url')
					}
				]
			})
		});

		const result: Task = await res.json();

		goto(`${base}/__admin/jobs/${result.id}`);
	}
</script>

<svelte:head>
	<title>New Job - Admin - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
	<form on:submit|preventDefault={handleSubmit}>
		<div class="grid grid-cols-1 gap-x-6 gap-y-8 sm:grid-cols-6">
			<div class="sm:col-span-2">
				<label for="type" class="block text-sm font-medium leading-6 text-gray-900">Type</label>
				<div class="mt-2">
					<select
						id="type"
						name="type"
						class="mt-2 block w-full rounded-md border-0 py-1.5 pl-3 pr-10 text-gray-900 ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-indigo-600 sm:text-sm sm:leading-6"
					>
						<option value="re-index">{display.get('re-index')}</option>
					</select>
				</div>
			</div>

			<div class="col-span-full">
				<label for="search-param-url" class="block text-sm font-medium leading-6 text-gray-900"
					>Search Param URL</label
				>
				<div class="mt-2">
					<input
						type="text"
						name="search-param-url"
						id="search-param-url"
						class="block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
					/>
				</div>
			</div>
		</div>

		<div class="mt-6 flex items-center justify-end gap-x-6">
			<button type="button" class="text-sm font-semibold leading-6 text-gray-900">Cancel</button>
			<button
				type="submit"
				class="rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
			>
				Submit New Job
			</button>
		</div>
	</form>
</main>
