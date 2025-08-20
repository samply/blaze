<script lang="ts">
  import type { CapabilityStatementRestResource } from 'fhir/r4';
  import type { RestfulInteraction } from '$lib/fhir.js';
  import { Check, XMark } from 'svelte-heros-v2';

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
    <Check class="mx-auto w-5 h-5 text-green-600 dark:text-green-400" />
  {:else}
    <XMark class="mx-auto w-5 h-5 text-red-600 dark:text-red-400" />
  {/if}
</td>
