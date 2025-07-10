<script lang="ts">
  import type { Feature } from './+layout';

  let { feature }: { feature: Feature } = $props();
</script>

<tr>
  <td class="whitespace-nowrap py-2 pl-4 pr-3 text-sm text-gray-500 sm:pl-0">{feature.name}</td>
  <td class="whitespace-nowrap py-2 pl-4 pr-3 text-sm text-gray-500 sm:pl-0">{feature.toggle}</td>
  <td
    class="whitespace-nowrap py-2 pl-4 pr-3 text-sm text-gray-500 sm:pl-0"
    class:font-medium={feature.enabled}
    class:text-gray-900={feature.enabled}>{feature.enabled}</td
  >
</tr>
