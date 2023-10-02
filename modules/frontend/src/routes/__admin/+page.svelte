<script lang="ts">
	import BreadcrumbEntryHome from '../breadcrumb-entry-home.svelte';
	import BreadcrumbEntryAdmin from './breadcrumb-entry-admin.svelte';
	import DbCard from './db-card.svelte';

	//export let data;
</script>

<svelte:head>
	<title>Admin - Blaze</title>
</svelte:head>

<header class="fixed w-full bg-white shadow z-10">
	<div class="mx-auto max-w-7xl px-4 py-4 sm:px-6 lg:px-8">
		<nav class="flex" aria-label="Breadcrumb">
			<ol class="flex items-center py-0.5 space-x-4">
				<BreadcrumbEntryHome />
				<BreadcrumbEntryAdmin />
			</ol>
		</nav>
	</div>
</header>

<main class="pt-14">
	<div class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
		<div>
			<h2 class="text-base font-semibold leading-6 text-gray-900 mb-2">Databases</h2>
			<ul class="grid grid-cols-1 gap-4 sm:grid-cols-3">
				<DbCard id="index" name="Index" />
				<DbCard id="transaction" name="Transaction" />
				<DbCard id="resource" name="Resource" />
			</ul>
		</div>
	</div>
</main>
