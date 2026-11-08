<script lang="ts">
  import { resolve } from '$app/paths';

  import Dropdown from '#lib/tailwind/dropdown.svelte';
  import DropdownItem from '#lib/tailwind/dropdown/item.svelte';

  interface Props {
    type: string;
    id: string;
    operations: string[];
  }

  let { type, id, operations }: Props = $props();
</script>

{#if operations.length > 0}
  <Dropdown name="Operations">
    {#each operations as operation (operation)}
      {#if operation === 'lookup'}
        <DropdownItem name="$lookup" url={resolve('/CodeSystem/[id=id]/$lookup', { id })} />
      {:else if operation === 'expand'}
        <DropdownItem name="$expand" url={resolve('/ValueSet/[id=id]/$expand', { id })} />
      {:else if operation === 'validate-code' && type === 'CodeSystem'}
        <DropdownItem
          name="$validate-code"
          url={resolve('/CodeSystem/[id=id]/$validate-code', { id })}
        />
      {:else if operation === 'validate-code' && type === 'ValueSet'}
        <DropdownItem
          name="$validate-code"
          url={resolve('/ValueSet/[id=id]/$validate-code', { id })}
        />
      {:else if operation === 'graph'}
        <DropdownItem name="$graph" url={resolve('/[type=type]/[id=id]/$graph', { type, id })} />
      {/if}
    {/each}
  </Dropdown>
{/if}
