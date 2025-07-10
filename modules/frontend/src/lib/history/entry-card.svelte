<script lang="ts">
  import type { FhirObjectBundleEntry } from '$lib/resource/resource-card.js';
  import ResourceCard from '$lib/resource/resource-card.svelte';
  import DeletedCard from './deleted-card.svelte';
  import Badge from './badge.svelte';

  interface Props {
    entry: FhirObjectBundleEntry;
  }

  let { entry }: Props = $props();
</script>

{#if entry.fhirObject}
  <ResourceCard resource={entry.fhirObject} embedded={true}>
    {#snippet header()}
      <Badge {entry} />
    {/snippet}
  </ResourceCard>
{:else if entry.request?.method === 'DELETE' && entry.response?.lastModified}
  <DeletedCard request={entry.request} lastModified={entry.response.lastModified} />
{/if}
