<script lang="ts">
  import prettyBytes from 'pretty-bytes';

  interface Props {
    level: number;
    fileSize: number;
    numFiles: number;
  }

  let { level, fileSize, numFiles }: Props = $props();
</script>

<tr>
  <td
    class="py-2 pr-3 pl-4 text-sm font-medium whitespace-nowrap text-gray-900 sm:pl-0 dark:text-gray-100"
    >{level}</td
  >
  <td class="px-3 py-2 text-right text-sm whitespace-nowrap text-gray-500 dark:text-gray-400"
    >{numFiles}</td
  >
  <td class="px-3 py-2 text-right text-sm whitespace-nowrap text-gray-500 dark:text-gray-400"
    >{prettyBytes(fileSize, { binary: true, maximumFractionDigits: 1 })}</td
  >
</tr>
