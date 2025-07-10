<script lang="ts">
  import type { FhirPrimitive, Type } from '$lib/resource/resource-card.js';
  import { base } from '$app/paths';

  interface Props {
    value: FhirPrimitive;
  }

  let { value }: Props = $props();

  function calcTargetType(type: Type): string | undefined {
    const targetPrefix = 'http://hl7.org/fhir/StructureDefinition/';
    const target = type.targetProfile !== undefined ? type.targetProfile[0] : undefined;
    return target !== undefined
      ? target.startsWith(targetPrefix)
        ? target.substring(targetPrefix.length)
        : undefined
      : undefined;
  }

  let targetType = $derived(calcTargetType(value.type));
</script>

{#if targetType}
  <a
    href="{base}/{targetType}?url={value.value}"
    class="font-medium text-indigo-600 hover:text-indigo-500">{value.value}</a
  >
{:else}
  {value.value}
{/if}
