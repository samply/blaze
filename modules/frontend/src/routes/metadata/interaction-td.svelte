<script lang="ts">
  import type { CapabilityStatementRestResource } from 'fhir/r4';
  import type { RestfulInteraction } from '$lib/fhir.js';
  import Check from './check.svelte';
  import XMark from './x-mark.svelte';

  interface Props {
    resource: CapabilityStatementRestResource;
    interaction: RestfulInteraction;
  }

  let { resource, interaction }: Props = $props();

  function hasInteraction(
    resource: CapabilityStatementRestResource,
    code: RestfulInteraction
  ): boolean {
    return resource.interaction?.find((i) => i.code == code) !== undefined;
  }
</script>

<td class="py-2">
  {#if hasInteraction(resource, interaction)}
    <Check />
  {:else}
    <XMark />
  {/if}
</td>
