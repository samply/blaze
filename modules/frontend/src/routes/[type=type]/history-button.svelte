<script lang="ts">
  import type { RouteParams } from './$types';

  import { resolve } from '$app/paths';

  let params: RouteParams = $props();
</script>

<a
  href={resolve('/[type=type]/_history?_summary=true', params)}
  class="flex-none rounded-md bg-white px-2 py-1 text-sm font-semibold text-gray-900 ring-1 ring-gray-300 ring-inset hover:bg-gray-50 dark:bg-gray-800 dark:text-gray-100 dark:ring-gray-500 hover:dark:bg-gray-700"
  title="{params.type} History">History</a
>
