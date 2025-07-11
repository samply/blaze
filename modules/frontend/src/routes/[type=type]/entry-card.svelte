<script lang="ts">
  import type { FhirObjectBundleEntry } from '$lib/resource/resource-card';
  import ResourceCard from '$lib/resource/resource-card.svelte';
  import SearchModeBadge from '$lib/history/search-mode-badge.svelte';

  interface Props {
    entry: FhirObjectBundleEntry;
  }

  let { entry }: Props = $props();
</script>

{#if entry.fhirObject}
  <ResourceCard resource={entry.fhirObject} embedded={true}>
    {#snippet header()}
      <SearchModeBadge mode={entry.search?.mode || 'unknown'} />
    {/snippet}
  </ResourceCard>
{/if}
