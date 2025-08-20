<script lang="ts">
  import { resolve } from '$app/paths';

  import Card from '$lib/tailwind/simple-logo-card.svelte';
  import { CircleStack, MagnifyingGlass } from 'svelte-heros-v2';
</script>

<svelte:head>
  <title>Create New Job - Admin - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl py-4 sm:px-6 lg:px-8">
  <h1 class="text-base font-semibold leading-6 text-gray-900">Please Choose</h1>

  <ul role="list" class="mt-4 grid grid-cols-1 gap-x-6 gap-y-8 lg:grid-cols-3 xl:gap-x-8">
    <Card href={resolve('/__admin/jobs/new/re-index')} title="(Re)Index a Search Parameter">
      <MagnifyingGlass class="size-7" />
    </Card>
    <Card href={resolve('/__admin/jobs/new/compact')} title="Compact a Database Column Family">
      <CircleStack class="size-7" />
    </Card>
  </ul>
</main>
