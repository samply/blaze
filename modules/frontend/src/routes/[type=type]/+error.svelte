<script lang="ts">
  import { page } from '$app/state';

  import Breadcrumb from '$lib/breadcrumb.svelte';
  import BreadcrumbEntryHome from '$lib/breadcrumb/home.svelte';
  import BreadcrumbEntryType from '$lib/breadcrumb/type.svelte';

  import SearchForm from './search-form.svelte';
  import ErrorCard from '$lib/error-card.svelte';
</script>

<svelte:head>
  <title>{page.params.type} - Blaze</title>
</svelte:head>

<header class="mx-auto max-w-7xl sm:px-6 lg:px-8">
  <Breadcrumb>
    <BreadcrumbEntryHome />
    <BreadcrumbEntryType />
  </Breadcrumb>
</header>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8 flex flex-col gap-4">
  <SearchForm searchParams={page.data.searchParams || []} />
  <ErrorCard />
</main>
