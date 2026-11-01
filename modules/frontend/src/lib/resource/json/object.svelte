<script lang="ts">
  import Property from './property.svelte';

  interface Props {
    indent?: number;
    object: object;
  }

  let { indent = 0, object }: Props = $props();

  let entries = $derived(Object.entries(object));
</script>

{#if entries.length === 0}{'{}'}{:else}{'{\n'}{#each entries as [name, value], index (name)}<Property
      indent={indent + 4}
      isLast={index + 1 === entries.length}
      {name}
      {value}
    />{/each}{' '.repeat(indent)}}{/if}
