<script lang="ts">
  import type { FhirObjectBundleEntry } from '$lib/resource/resource-card';
  import Badge from '$lib/tailwind/badge.svelte';

  interface Props {
    entry: FhirObjectBundleEntry;
  }

  const subsettedSystem = 'http://terminology.hl7.org/CodeSystem/v3-ObservationValue';

  function isSubsetted(entry: FhirObjectBundleEntry): boolean {
    const tags = entry.resource?.meta?.tag;
    return tags?.filter((c) => c.system === subsettedSystem)[0]?.code === 'SUBSETTED';
  }

  let { entry }: Props = $props();
</script>

{#if isSubsetted(entry)}
  <div class="ml-2 flex flex-shrink-0">
    <Badge value="subsetted" title="Resource is incomplete" color="orange" />
  </div>
{/if}
