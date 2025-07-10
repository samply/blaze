<script lang="ts">
  import type { Setting } from './+layout';

  let { name, value, masked, defaultValue }: Setting = $props();
</script>

<tr>
  <td class="whitespace-nowrap py-2 pl-4 pr-3 text-sm text-gray-500 sm:pl-0">{name}</td>
  <td
    class="whitespace-nowrap py-2 pl-4 pr-3 text-sm text-gray-500 sm:pl-0"
    class:font-medium={value !== defaultValue}
    class:text-gray-900={value !== defaultValue}>{masked ? '***' : value}</td
  >
  <td class="whitespace-nowrap py-2 pl-4 pr-3 text-sm text-gray-500 sm:pl-0"
    >{defaultValue || '–'}</td
  >
</tr>
