<script lang="ts">
  import {
    type FhirPrimitive,
    type FhirProperty,
    isPrimitive,
    wrapPrimitiveExtensions
  } from '../resource-card.js';
  import ArrayValue from './array.svelte';
  import Value from './value.svelte';

  interface Props {
    indent: number;
    isLast: boolean;
    property: FhirProperty;
  }

  let { indent, isLast, property }: Props = $props();

  let primitiveExtensions =
    !Array.isArray(property.value) && isPrimitive(property.value.type)
      ? (property.value as FhirPrimitive).extensions
      : undefined;
</script>

{' '.repeat(indent)}<span class="text-orange-700 dark:text-orange-200">"{property.name}"</span
>{': '}{#if Array.isArray(property.value)}<ArrayValue
    {indent}
    values={property.value}
  />{:else}<Value
    {indent}
    value={property.value}
  />{/if}{#if primitiveExtensions !== undefined}{',\n'}{' '.repeat(indent)}<span
    class="text-orange-700 dark:text-orange-200">"_{property.name}"</span
  >{': '}<Value {indent} value={wrapPrimitiveExtensions(primitiveExtensions)} />{/if}{isLast
  ? '\n'
  : ',\n'}
