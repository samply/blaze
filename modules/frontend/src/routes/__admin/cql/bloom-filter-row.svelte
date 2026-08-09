<script lang="ts">
  import prettyBytes from 'pretty-bytes';
  import type { BloomFilter } from './+page';

  interface Props {
    bloomFilter: BloomFilter;
  }

  let { bloomFilter }: Props = $props();
</script>

<tr>
  <td
    class="py-2 pr-3 pl-4 text-right text-sm whitespace-nowrap text-gray-500 sm:pl-0 dark:text-gray-400"
    >{bloomFilter.t}</td
  >
  <td
    class="py-2 pr-3 pl-4 font-mono text-sm whitespace-nowrap text-gray-500 sm:pl-0 dark:text-gray-400"
    >{bloomFilter.hash.substring(0, 8)}</td
  >
  <td
    class="py-2 pr-3 pl-4 text-right text-sm whitespace-nowrap text-gray-500 sm:pl-0 dark:text-gray-400"
    >{bloomFilter.patientCount}</td
  >
  <td
    class="py-2 pr-3 pl-4 text-right text-sm whitespace-nowrap text-gray-500 sm:pl-0 dark:text-gray-400"
    >{prettyBytes(bloomFilter.memSize, { binary: true, maximumFractionDigits: 1 })}</td
  >
</tr>
