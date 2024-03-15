<script lang="ts">
	import { page } from '$app/stores';

	import BreadcrumbEntryHome from '$lib/breadcrumb/home.svelte';
	import BreadcrumbEntryType from '$lib/breadcrumb/type.svelte';

	import SearchForm from './search-form.svelte';
	import ErrorCard from './../error-card.svelte';
</script>

<svelte:head>
	<title>{$page.params.type} - Blaze</title>
</svelte:head>

<header class="mx-auto max-w-7xl sm:px-6 lg:px-8">
	<nav class="flex pl-8 py-4 border-b border-gray-200" aria-label="Breadcrumb">
		<ol class="flex items-center py-0.5 space-x-4">
			<BreadcrumbEntryHome />
			<BreadcrumbEntryType />
		</ol>
	</nav>
</header>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8 flex flex-col gap-4">
	<SearchForm capabilityStatement={$page.data.capabilityStatement} />
	<ErrorCard />
</main>
