<script lang="ts">
  import ArrayValue from './array.svelte';
  import ObjectValue from './object.svelte';
  import PrimitiveValue from './primitive-value.svelte';

  interface Props {
    indent: number;
    insideArray?: boolean;
    value: unknown;
  }

  let { indent, insideArray = false, value }: Props = $props();
</script>

{insideArray ? ' '.repeat(indent) : ''}{#if Array.isArray(value)}<ArrayValue
    {indent}
    values={value}
  />{:else if typeof value === 'object' && value !== null}<ObjectValue
    {indent}
    object={value}
  />{:else}<PrimitiveValue {value} />{/if}
