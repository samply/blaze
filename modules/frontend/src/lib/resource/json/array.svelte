<script lang="ts">
  import Value from './value.svelte';

  interface Props {
    indent: number;
    values: unknown[];
  }

  let { indent, values }: Props = $props();

  const maxLength = 100;
  let length = $derived(Math.min(values.length, maxLength));
</script>

{#if values.length === 0}[]{:else}[{#if length < values.length}&nbsp;!!!&nbsp;SHORTENED&nbsp;-&nbsp;DO&nbsp;NOT&nbsp;COPY&nbsp;!!!{/if}{'\n'}{#each values.slice(0, maxLength) as value, index (index)}<Value
      indent={indent + 4}
      insideArray={true}
      {value}
    />{index < length - 1 ? ',\n' : '\n'}{/each}{' '.repeat(indent)}]{/if}
