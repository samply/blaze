<script lang="ts">
  import type { FhirObject, FhirPrimitive } from '../resource-card.js';
  import Value from './value.svelte';

  interface Props {
    indent: number;
    values: (FhirObject | FhirPrimitive)[];
  }

  let { indent, values }: Props = $props();

  const maxLength = 100;
  let length = Math.min(values.length, maxLength);
</script>

[{#if length < values.length}&nbsp;!!!&nbsp;SHORTENED&nbsp;-&nbsp;DO&nbsp;NOT&nbsp;COPY&nbsp;!!!{/if}{'\n'}{#each values.slice(0, maxLength) as value, index}<Value
    indent={indent + 4}
    insideArray={true}
    {value}
  />{index < length - 1 ? ',\n' : '\n'}{/each}{' '.repeat(indent)}]
