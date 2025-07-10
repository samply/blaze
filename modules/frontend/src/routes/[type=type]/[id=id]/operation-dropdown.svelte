<script lang="ts">
  import { base } from '$app/paths';
  import { page } from '$app/state';

  import Dropdown from '$lib/tailwind/dropdown.svelte';
  import DropdownItem from '$lib/tailwind/dropdown/item.svelte';
</script>

{#if ['CodeSystem', 'ValueSet', 'Patient'].includes(page.params.type)}
  <Dropdown name="Operations">
    {#if page.params.type === 'CodeSystem'}
      <DropdownItem name="$validate-code" url="{base}/CodeSystem/{page.params.id}/$validate-code" />
    {:else if page.params.type === 'ValueSet'}
      <DropdownItem name="$expand" url="{base}/ValueSet/{page.params.id}/$expand" />
      <DropdownItem name="$validate-code" url="{base}/ValueSet/{page.params.id}/$validate-code" />
    {:else if page.params.type === 'Patient'}
      <DropdownItem name="$graph" url="{base}/Patient/{page.params.id}/$graph" />
    {/if}
  </Dropdown>
{/if}
