<script lang="ts">
	import type { PageData } from './$types';

	import { page } from '$app/state';

	import BreadcrumbEntryHome from '$lib/breadcrumb/home.svelte';
	import BreadcrumbEntryType from '$lib/breadcrumb/type.svelte';
	import BreadcrumbEntryResource from '$lib/breadcrumb/resource.svelte';
	import BreadcrumbEntryVersion from '$lib/breadcrumb/version.svelte';
	import ResourceCard from '$lib/resource/resource-card.svelte';

	interface Props {
		data: PageData;
	}

	let { data }: Props = $props();
</script>

<svelte:head>
	<title>Version ${page.params.vid} - {page.params.type}/{page.params.id} - Blaze</title>
</svelte:head>

<header class="mx-auto max-w-7xl sm:px-6 lg:px-8">
	<nav class="flex pl-8 py-4 border-b border-gray-200" aria-label="Breadcrumb">
		<ol class="flex items-center py-0.5 space-x-4">
			<BreadcrumbEntryHome />
			<BreadcrumbEntryType />
			<BreadcrumbEntryResource />
			<BreadcrumbEntryVersion />
		</ol>
	</nav>
</header>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
	<ResourceCard resource={data.resource} />
</main>
