<script lang="ts">
  import type { Feature } from './+layout.server.js';

  let { feature }: { feature: Feature } = $props();
</script>

<tr>
  <td class="py-2 pr-3 pl-4 text-sm whitespace-nowrap text-gray-500 sm:pl-0 dark:text-gray-400"
    >{feature.name}</td
  >
  <td class="py-2 pr-3 pl-4 text-sm whitespace-nowrap text-gray-500 sm:pl-0 dark:text-gray-400"
    >{feature.toggle}</td
  >
  <td
    class="py-2 pr-3 pl-4 text-sm whitespace-nowrap text-gray-500 sm:pl-0 dark:text-gray-400"
    class:font-medium={feature.enabled}
    class:text-gray-900={feature.enabled}
    class:dark:text-gray-100={feature.enabled}>{feature.enabled}</td
  >
</tr>
