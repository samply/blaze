<script lang="ts">
  import type { BundleEntry, FhirResource } from 'fhir/r4';
  import { isSubsetted } from '#lib/resource/subsetted.js';
  import Badge from '#lib/tailwind/badge.svelte';

  interface Props {
    entry: BundleEntry<FhirResource>;
  }

  let { entry }: Props = $props();
</script>

{#if isSubsetted(entry)}
  <div class="ml-2 flex shrink-0">
    <Badge
      value="subsetted"
      title="Returned in summary mode (_summary=true); some elements are omitted."
      color="orange"
    />
  </div>
{/if}
