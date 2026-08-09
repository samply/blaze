<script lang="ts">
  import type { Setting } from './+layout';

  let { name, value, masked, defaultValue }: Setting = $props();
</script>

<tr>
  <td class="py-2 pr-3 pl-4 text-sm whitespace-nowrap text-gray-500 sm:pl-0 dark:text-gray-400"
    >{name}</td
  >
  <td
    class="py-2 pr-3 pl-4 text-sm text-gray-500 sm:pl-0 dark:text-gray-400"
    class:font-medium={value !== defaultValue}
    class:text-gray-900={value !== defaultValue}
    class:dark:text-gray-100={value !== defaultValue}>{masked ? '***' : value}</td
  >
  <td class="py-2 pr-3 pl-4 text-sm text-gray-500 sm:pl-0 dark:text-gray-400"
    >{defaultValue || '–'}</td
  >
</tr>
