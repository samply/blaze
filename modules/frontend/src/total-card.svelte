<script lang="ts">
	import { type Bundle, bundleLink } from './fhir';
	import { dev } from '$app/environment';
	import { base } from '$app/paths';
	import { fade } from 'svelte/transition';
	import { quintIn } from 'svelte/easing';

	export let bundle: Bundle<unknown>;
	export let showFirstLink = false;

	$: firstLinkUrl = bundleLink(bundle, 'first')?.url;
	$: nextLinkUrl = bundleLink(bundle, 'next')?.url;
</script>

<div
	in:fade|global={{ duration: 300, easing: quintIn }}
	class="flex gap-2 bg-white shadow sm:rounded-lg px-4 py-5 sm:px-6"
>
	<slot />
	{#if showFirstLink && firstLinkUrl}
		<a
			href={dev ? firstLinkUrl.replace('http://localhost:8080/fhir', base) : firstLinkUrl}
			class="flex-none rounded-md bg-white px-3 py-2 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50"
			>First</a
		>
	{/if}
	{#if nextLinkUrl}
		<a
			href={dev ? nextLinkUrl.replace('http://localhost:8080/fhir', base) : nextLinkUrl}
			class="flex-none w-20 inline-flex items-center gap-x-1.5 rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
		>
			Next
			<svg class="-mr-0.5 h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
				<path
					fill-rule="evenodd"
					d="M3 10a.75.75 0 01.75-.75h10.638L10.23 5.29a.75.75 0 111.04-1.08l5.5 5.25a.75.75 0 010 1.08l-5.5 5.25a.75.75 0 11-1.04-1.08l4.158-3.96H3.75A.75.75 0 013 10z"
					clip-rule="evenodd"
				/>
			</svg>
		</a>
	{/if}
	{#if showFirstLink && firstLinkUrl && !nextLinkUrl}
		<button
			type="button"
			disabled
			class="flex-none w-20 inline-flex items-center gap-x-1.5 rounded-md bg-indigo-600 px-3 py-2 text-sm font-semibold text-white shadow-sm opacity-50"
		>
			Next
			<svg class="-mr-0.5 h-5 w-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
				<path
					fill-rule="evenodd"
					d="M3 10a.75.75 0 01.75-.75h10.638L10.23 5.29a.75.75 0 111.04-1.08l5.5 5.25a.75.75 0 010 1.08l-5.5 5.25a.75.75 0 11-1.04-1.08l4.158-3.96H3.75A.75.75 0 013 10z"
					clip-rule="evenodd"
				/>
			</svg>
		</button>
	{/if}
</div>
