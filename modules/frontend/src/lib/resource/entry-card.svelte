<script lang="ts">
  import type { FhirObjectBundleEntry } from '$lib/resource/resource-card.js';
  import ResourceCard from '$lib/resource/resource-card.svelte';
  import DeletedCard from '../history/deleted-card.svelte';
  import OutcomeCard from './outcome-card.svelte';
  import Badge from '../history/badge.svelte';

  interface Props {
    entry: FhirObjectBundleEntry;
  }

  let { entry }: Props = $props();
</script>

{#if entry.fhirObject}
  {#if entry.resource?.resourceType === 'OperationOutcome'}
    <OutcomeCard outcome={entry.resource}></OutcomeCard>
  {:else}
    <ResourceCard resource={entry.fhirObject} embedded={true}>
      {#snippet header()}
        <Badge {entry} />
      {/snippet}
    </ResourceCard>
  {/if}
{:else if entry.request?.method === 'DELETE' && entry.response?.lastModified}
  <DeletedCard request={entry.request} lastModified={entry.response.lastModified} />
{/if}
