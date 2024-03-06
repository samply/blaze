<script lang="ts">
	import type { PageData } from './$types';

	import { page } from '$app/stores';

	import BreadcrumbEntryHome from '../../../../breadcrumb-entry-home.svelte';
	import BreadcrumbEntryType from '../../../breadcrumb-entry-type.svelte';
	import BreadcrumbEntryResource from './../../breadcrumb-entry-resource.svelte';
	import BreadcrumbEntryVersion from './breadcrumb-entry-version.svelte';
	import ResourceCard from '$lib/resource/resource-card.svelte';

	export let data: PageData;
</script>

<svelte:head>
	<title>Version ${$page.params.vid} - {$page.params.type}/{$page.params.id} - Blaze</title>
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
