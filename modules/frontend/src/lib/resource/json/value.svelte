<script lang="ts">
  import { type FhirObject, type FhirPrimitive, isPrimitive } from '../resource-card.js';
  import PrimitiveValue from './primitive-value.svelte';
  import Object from './object.svelte';

  interface Props {
    indent: number;
    insideArray?: boolean;
    value: FhirObject | FhirPrimitive;
  }

  let { indent, insideArray = false, value }: Props = $props();

  function toPrimitive(value: FhirObject | FhirPrimitive): FhirPrimitive {
    return value as FhirPrimitive;
  }

  function toObject(value: FhirObject | FhirPrimitive): FhirObject {
    return value as FhirObject;
  }
</script>

{#if isPrimitive(value.type)}{insideArray ? ' '.repeat(indent) : ''}<PrimitiveValue
    value={toPrimitive(value)}
  />{:else}<Object {indent} {insideArray} object={toObject(value)} />{/if}
