<script lang="ts">
  import { resolve } from '$app/paths';

  import Dropdown from '$lib/tailwind/dropdown.svelte';
  import DropdownItem from '$lib/tailwind/dropdown/item.svelte';
</script>

<Dropdown name="Operations">
  <DropdownItem name="$expand" url={resolve('/ValueSet/$expand')} />
  <DropdownItem name="$validate-code" url={resolve('/ValueSet/$validate-code')} />
</Dropdown>
