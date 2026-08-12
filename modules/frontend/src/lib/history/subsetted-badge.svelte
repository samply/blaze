<script lang="ts">
  import type { FhirObjectBundleEntry } from '$lib/resource/resource-card';
  import { isSubsetted } from '$lib/resource/subsetted.js';
  import Badge from '$lib/tailwind/badge.svelte';

  interface Props {
    entry: FhirObjectBundleEntry;
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
