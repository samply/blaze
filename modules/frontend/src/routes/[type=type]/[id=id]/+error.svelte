<script lang="ts">
	import { base } from '$app/paths';
	import { page } from '$app/stores';

	import BreadcrumbEntryHome from '../../breadcrumb-entry-home.svelte';
	import BreadcrumbEntryType from '../breadcrumb-entry-type.svelte';
	import BreadcrumbEntryResource from './breadcrumb-entry-resource.svelte';
	import ErrorCard from './../../error-card.svelte';
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
		<ErrorCard>
			{#if $page.status == 404}
				<a href="{base}/{$page.params.type}" class="text-sm font-semibold text-gray-900"
					>Go to {$page.params.type}s <span aria-hidden="true">&rarr;</span></a
				>
			{:else if $page.status == 410}
				<a
					href="{base}/{$page.params.type}/{$page.params.id}/_history"
					class="text-sm font-semibold text-gray-900"
					>Go to History <span aria-hidden="true">&rarr;</span></a
				>
			{/if}
		</ErrorCard>
	</div>
</main>
