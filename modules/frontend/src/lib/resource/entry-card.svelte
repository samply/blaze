<script lang="ts">
  import type { BundleEntry, FhirResource } from 'fhir/r4';
  import ResourceCard from '#lib/resource/resource-card.svelte';
  import DeletedCard from '../history/deleted-card.svelte';
  import OutcomeCard from './outcome-card.svelte';
  import EntryBadges from './entry-badges.svelte';

  interface Props {
    entry: BundleEntry<FhirResource>;
  }

  let { entry }: Props = $props();
</script>

{#if entry.resource}
  {#if entry.resource?.resourceType === 'OperationOutcome'}
    <OutcomeCard outcome={entry.resource}></OutcomeCard>
  {:else}
    <ResourceCard resource={entry.resource} embedded={true}>
      {#snippet header()}
        <EntryBadges {entry} />
      {/snippet}
    </ResourceCard>
  {/if}
{:else if entry.request?.method === 'DELETE' && entry.response?.lastModified}
  <DeletedCard request={entry.request} lastModified={entry.response.lastModified} />
{/if}
