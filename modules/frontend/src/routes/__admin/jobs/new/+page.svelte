<script lang="ts">
	import type { ActionData } from './$types';
	import { base } from '$app/paths';

	const display = new Map();
	display.set('re-index', '(Re)Index a Search Parameter');

	export let form: ActionData;
</script>

<svelte:head>
	<title>Create New Job - Admin - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
	<form method="POST">
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
				{#if form?.incorrect}
					<div class="relative mt-2 rounded-md shadow-sm">
						<input
							type="text"
							name="search-param-url"
							id="search-param-url"
							value={form?.searchParamUrl || ''}
							class="block w-full rounded-md border-0 py-1.5 pr-10 text-red-900 ring-1 ring-inset ring-red-300 placeholder:text-red-300 focus:ring-2 focus:ring-inset focus:ring-red-500 sm:text-sm sm:leading-6"
						/>
						<div class="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-3">
							<svg
								class="h-5 w-5 text-red-500"
								viewBox="0 0 20 20"
								fill="currentColor"
								aria-hidden="true"
							>
								<path
									fill-rule="evenodd"
									d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-8-5a.75.75 0 01.75.75v4.5a.75.75 0 01-1.5 0v-4.5A.75.75 0 0110 5zm0 10a1 1 0 100-2 1 1 0 000 2z"
									clip-rule="evenodd"
								/>
							</svg>
						</div>
					</div>
					<p class="mt-2 text-sm text-red-600">{form.msg}</p>
				{:else}
					<div class="relative mt-2 rounded-md shadow-sm">
						<input
							type="text"
							name="search-param-url"
							id="search-param-url"
							value={form?.searchParamUrl || ''}
							class="block w-full rounded-md border-0 py-1.5 text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
						/>
					</div>
				{/if}
			</div>
		</div>

		<div class="mt-6 flex items-center justify-end gap-x-6">
			<a class="text-sm font-semibold leading-6 text-gray-900" href="{base}/__admin/jobs">Cancel</a>
			<button
				type="submit"
				class="rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
				>Submit New Job</button
			>
		</div>
	</form>
</main>
