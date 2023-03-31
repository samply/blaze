<script lang="ts">
	import { page } from '$app/stores';

	import BreadcrumbEntryHome from '../breadcrumb-entry-home.svelte';
	import BreadcrumbEntryType from './breadcrumb-entry-type.svelte';
	import SearchForm from './search-form.svelte';
	import ErrorCard from './../error-card.svelte';
</script>

<svelte:head>
	<title>{$page.params.type} - Blaze</title>
</svelte:head>

<header class="fixed w-full bg-white shadow">
	<div class="mx-auto max-w-7xl px-4 py-4 sm:px-6 lg:px-8">
		<nav class="flex" aria-label="Breadcrumb">
			<ol class="flex items-center py-0.5 space-x-4">
				<BreadcrumbEntryHome />
				<BreadcrumbEntryType />
			</ol>
		</nav>
	</div>
</header>

<main class="pt-14">
	<div class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8 flex flex-col gap-4">
		<SearchForm capabilityStatement={$page.data.capabilityStatement} />
		<ErrorCard />
	</div>
</main>
