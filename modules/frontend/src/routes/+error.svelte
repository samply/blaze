<script lang="ts">
	import BreadcrumbEntryHome from './breadcrumb-entry-home.svelte';
	import ErrorCard from './error-card.svelte';
</script>

<svelte:head>
	<title>Blaze</title>
</svelte:head>

<header class="fixed w-full bg-white shadow">
	<div class="mx-auto max-w-7xl px-4 py-4 sm:px-6 lg:px-8">
		<nav class="flex" aria-label="Breadcrumb">
			<ol class="flex items-center py-0.5 space-x-4">
				<BreadcrumbEntryHome />
			</ol>
		</nav>
	</div>
</header>

<main class="pt-14">
	<div class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
		<ErrorCard />
	</div>
</main>
