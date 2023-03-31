<script lang="ts">
	import BreadcrumbEntryHome from '../../breadcrumb-entry-home.svelte';
	import BreadcrumbEntryType from '../breadcrumb-entry-type.svelte';
	import BreadcrumbEntryResource from './breadcrumb-entry-resource.svelte';
	import ResourceCard from '../../../resource/resource-card.svelte';
	import { base } from '$app/paths';
	import { page } from '$app/stores';

	export let data;
</script>

<svelte:head>
	<title>{$page.params.type}/{$page.params.id} - Blaze</title>
</svelte:head>

<header class="fixed w-full bg-white shadow">
	<div class="mx-auto max-w-7xl px-4 py-4 sm:px-6 lg:px-8">
		<nav class="flex" aria-label="Breadcrumb">
			<ol class="flex items-center py-0.5 space-x-4">
				<BreadcrumbEntryHome />
				<BreadcrumbEntryType />
				<BreadcrumbEntryResource />
			</ol>
		</nav>
	</div>
</header>

<main class="pt-14">
	<div class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
		<ResourceCard resource={data.resource}>
			<div slot="header">
				<a
					href="{base}/{$page.params.type}/{$page.params.id}/_history"
					class="rounded bg-indigo-600 px-2 py-1 text-xs font-semibold text-white shadow-sm hover:bg-indigo-500 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600"
					title="Go to resource history">History</a
				>
			</div>
		</ResourceCard>
	</div>
</main>
