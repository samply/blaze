<script lang="ts">
  import type { RouteParams } from './$types';

  import { resolve } from '$app/paths';

  import Dropdown from '$lib/tailwind/dropdown.svelte';
  import DropdownItem from '$lib/tailwind/dropdown/item.svelte';

  let params: RouteParams = $props();
</script>

{#if ['CodeSystem', 'ValueSet', 'Patient'].includes(params.type)}
  <Dropdown name="Operations">
    {#if params.type === 'CodeSystem'}
      <DropdownItem
        name="$validate-code"
        url={resolve('/CodeSystem/[id=id]/$validate-code', params)}
      />
    {:else if params.type === 'ValueSet'}
      <DropdownItem name="$expand" url={resolve('/ValueSet/[id=id]/$expand', params)} />
      <DropdownItem
        name="$validate-code"
        url={resolve('/ValueSet/[id=id]/$validate-code', params)}
      />
    {:else if params.type === 'Patient'}
      <DropdownItem name="$graph" url={resolve('/[type=type]/[id=id]/$graph', params)} />
    {/if}
  </Dropdown>
{/if}
