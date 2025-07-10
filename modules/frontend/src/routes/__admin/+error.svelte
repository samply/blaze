<script lang="ts">
  import { base } from '$app/paths';

  import ErrorCard from '$lib/error-card.svelte';
</script>

<svelte:head>
  <title>Admin - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
  <ErrorCard homeHref="{base}/__admin" homeMsg="Go back to Overview" />
</main>
