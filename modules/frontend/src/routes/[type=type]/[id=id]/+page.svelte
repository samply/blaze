<script lang="ts">
	import type { PageData } from './$types';

	import { base } from '$app/paths';
	import { page } from '$app/state';

	import BreadcrumbEntryHome from '$lib/breadcrumb/home.svelte';
	import BreadcrumbEntryType from '$lib/breadcrumb/type.svelte';
	import BreadcrumbEntryResource from '$lib/breadcrumb/resource.svelte';

	import ResourceCard from '$lib/resource/resource-card.svelte';

	interface Props {
		data: PageData;
	}

	let { data }: Props = $props();
</script>

<svelte:head>
	<title>{page.params.type}/{page.params.id} - Blaze</title>
</svelte:head>

<header class="mx-auto max-w-7xl sm:px-6 lg:px-8">
	<nav class="flex pl-8 py-4 border-b border-gray-200" aria-label="Breadcrumb">
		<ol class="flex items-center py-0.5 space-x-4">
			<BreadcrumbEntryHome />
			<BreadcrumbEntryType />
			<BreadcrumbEntryResource />
		</ol>
	</nav>
</header>

{#if data.resource}
	<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
		<ResourceCard resource={data.resource}>
			{#snippet header()}
				<div>
					<a
						href="{base}/{page.params.type}/{page.params.id}/_history"
						class="rounded bg-indigo-600 px-2 py-1 text-xs font-semibold text-white hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
						title="Go to resource history">History</a
					>
				</div>
			{/snippet}
		</ResourceCard>
	</main>
{/if}
