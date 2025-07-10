<script lang="ts">
  import { base } from '$app/paths';
  import { page } from '$app/state';

  import Dropdown from '$lib/tailwind/dropdown.svelte';
  import DropdownItem from '$lib/tailwind/dropdown/item.svelte';
</script>

<Dropdown name="Operations">
  <DropdownItem name="$validate-code" url="{base}/CodeSystem/{page.params.id}/$validate-code" />
</Dropdown>
