<script lang="ts">
  import { page } from '$app/state';

  import Breadcrumb from '$lib/breadcrumb.svelte';
  import BreadcrumbEntryHome from '$lib/breadcrumb/home.svelte';
  import BreadcrumbEntryType from '$lib/breadcrumb/type.svelte';

  import SearchForm from './search-form.svelte';
  import ErrorCard from '$lib/error-card.svelte';
  import { emptySearchMetadata, type SearchMetadata } from '$lib/search-metadata.js';

  // Error components are not passed the route props, so read the params off the page state.
  let params = $derived(page.params as { type: string });

  // `page.data` is untyped, so annotate to keep the prop type-checked. The load
  // may have failed before the search metadata was fetched.
  let searchMetadata: SearchMetadata = $derived(page.data.searchMetadata ?? emptySearchMetadata());
</script>

<svelte:head>
  <title>{params.type} - Blaze</title>
</svelte:head>

<header class="mx-auto max-w-7xl sm:px-6 lg:px-8">
  <Breadcrumb>
    <BreadcrumbEntryHome />
    <BreadcrumbEntryType {...params} />
  </Breadcrumb>
</header>

<main class="mx-auto flex max-w-7xl flex-col py-4 sm:px-6 lg:px-8">
  <SearchForm {searchMetadata} type={params.type} />
  <ErrorCard />
</main>
