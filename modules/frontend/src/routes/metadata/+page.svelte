<script lang="ts">
  import type { PageProps } from './$types';

  import MetadataCard from './metadata-card.svelte';

  let { data }: PageProps = $props();
</script>

<svelte:head>
  <title>Metadata - Blaze</title>
</svelte:head>

<main class="mx-auto max-w-7xl sm:px-6 lg:px-8">
  <MetadataCard
    capabilityStatement={data.capabilityStatement}
    capabilityStatementObject={data.capabilityStatementObject}
  />
</main>
