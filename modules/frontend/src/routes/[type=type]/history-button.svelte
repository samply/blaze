<script lang="ts">
  import type { RouteParams } from './$types';

  import { resolve } from '$app/paths';

  let params: RouteParams = $props();
</script>

<a
  href={resolve('/[type=type]/_history', params)}
  class="flex-none rounded-md bg-white dark:bg-gray-800 px-2 py-1 text-sm font-semibold text-gray-900 dark:text-gray-100 ring-1 ring-inset ring-gray-300 dark:ring-gray-500 hover:bg-gray-50 hover:dark:bg-gray-700"
  title="{params.type} History">History</a
>
