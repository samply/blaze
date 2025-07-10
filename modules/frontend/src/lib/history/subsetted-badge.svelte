<script lang="ts">
  import type { FhirObjectBundleEntry } from '$lib/resource/resource-card';

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
    <p
      role="note"
      title="Some information has been removed from the resource."
      class="inline-flex items-center rounded-full bg-orange-100 px-2.5 py-0.5 text-xs font-medium text-orange-700"
    >
      subsetted
    </p>
  </div>
{/if}
