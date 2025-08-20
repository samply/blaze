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
    class="whitespace-nowrap py-2 pl-4 pr-3 text-sm font-medium text-gray-900 dark:text-gray-100 sm:pl-0"
    >{level}</td
  >
  <td class="whitespace-nowrap px-3 py-2 text-right text-sm text-gray-500 dark:text-gray-400"
    >{numFiles}</td
  >
  <td class="whitespace-nowrap px-3 py-2 text-right text-sm text-gray-500 dark:text-gray-400"
    >{prettyBytes(fileSize, { binary: true, maximumFractionDigits: 1 })}</td
  >
</tr>
