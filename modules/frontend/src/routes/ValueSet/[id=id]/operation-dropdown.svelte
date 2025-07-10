<script lang="ts">
  import { base } from '$app/paths';
  import { page } from '$app/state';

  import Dropdown from '$lib/tailwind/dropdown.svelte';
  import DropdownItem from '$lib/tailwind/dropdown/item.svelte';
</script>

<Dropdown name="Operations">
  <DropdownItem name="$expand" url="{base}/ValueSet/{page.params.id}/$expand" />
  <DropdownItem name="$validate-code" url="{base}/ValueSet/{page.params.id}/$validate-code" />
</Dropdown>
