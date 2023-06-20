<script lang="ts">
	import BreadcrumbEntryHome from '../../../../breadcrumb-entry-home.svelte';
	import BreadcrumbEntryType from '../../../breadcrumb-entry-type.svelte';
	import BreadcrumbEntryResource from './../../breadcrumb-entry-resource.svelte';
	import BreadcrumbEntryVersion from './breadcrumb-entry-version.svelte';
	import ResourceCard from '../../../../../resource/resource-card.svelte';
	import { page } from '$app/stores';

	export let data;
</script>

<svelte:head>
	<title>Version ${$page.params.vid} - {$page.params.type}/{$page.params.id} - Blaze</title>
</svelte:head>

<header class="fixed w-full bg-white shadow">
	<div class="mx-auto max-w-7xl px-4 py-4 sm:px-6 lg:px-8">
		<nav class="flex" aria-label="Breadcrumb">
			<ol class="flex items-center py-0.5 space-x-4">
				<BreadcrumbEntryHome />
				<BreadcrumbEntryType />
				<BreadcrumbEntryResource />
				<BreadcrumbEntryVersion />
			</ol>
		</nav>
	</div>
</header>

<main class="pt-14">
	<div class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
		<ResourceCard resource={data.resource} />
	</div>
</main>
