<script lang="ts">
  import { resolve } from '$app/paths';

  import Entry from './entry.svelte';
</script>

<Entry>
  <a
    href={resolve('/metadata')}
    class="ml-4 text-sm font-medium text-gray-500 dark:text-gray-400 hover:text-gray-700 hover:dark:text-gray-300"
    >Metadata</a
  >
</Entry>
