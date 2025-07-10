<script lang="ts">
  import prettyBytes from 'pretty-bytes';
  import type { BloomFilter } from './+page';

  interface Props {
    bloomFilter: BloomFilter;
  }

  let { bloomFilter }: Props = $props();
</script>

<tr>
  <td class="whitespace-nowrap py-2 pl-4 pr-3 text-right text-sm text-gray-500 sm:pl-0"
    >{bloomFilter.t}</td
  >
  <td class="whitespace-nowrap py-2 pl-4 pr-3 font-mono text-sm text-gray-500 sm:pl-0"
    >{bloomFilter.hash.substring(0, 8)}</td
  >
  <td class="whitespace-nowrap py-2 pl-4 pr-3 text-right text-sm text-gray-500 sm:pl-0"
    >{bloomFilter.patientCount}</td
  >
  <td class="whitespace-nowrap py-2 pl-4 pr-3 text-right text-sm text-gray-500 sm:pl-0"
    >{prettyBytes(bloomFilter.memSize, { binary: true, maximumFractionDigits: 1 })}</td
  >
</tr>
