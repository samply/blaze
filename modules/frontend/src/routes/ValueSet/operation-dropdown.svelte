<script lang="ts">
  import { base } from '$app/paths';

  import Dropdown from '$lib/tailwind/dropdown.svelte';
  import DropdownItem from '$lib/tailwind/dropdown/item.svelte';
</script>

<Dropdown name="Operations">
  <DropdownItem name="$validate-code" url="{base}/ValueSet/$validate-code" />
</Dropdown>
