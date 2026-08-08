<script lang="ts">
  import { resolve } from '$app/paths';
  import { pascalCase } from 'change-case';
  import prettyBytes from 'pretty-bytes';
  import prettyNum from '$lib/pretty-num.js';

  interface Props {
    dbId: string;
    name: string;
    estimateNumKeys: number;
    liveSstFilesSize: number;
    sizeAllMemTables: number;
  }

  let { dbId, name, estimateNumKeys, liveSstFilesSize, sizeAllMemTables }: Props = $props();
</script>

<tr>
  <td
    class="py-2 pr-3 pl-4 text-sm font-medium whitespace-nowrap text-gray-900 sm:pl-0 dark:text-gray-100"
  >
    <a
      href={resolve('/__admin/dbs/[dbId=id]/column-families/[cfId=id]', { dbId: dbId, cfId: name })}
      >{pascalCase(name)}</a
    >
  </td>
  <td
    class="hidden px-3 py-2 text-right text-sm whitespace-nowrap text-gray-500 sm:table-cell dark:text-gray-400"
    >{prettyNum(estimateNumKeys, { maximumFractionDigits: 1 })}</td
  >
  <td class="px-3 py-2 text-right text-sm whitespace-nowrap text-gray-500 dark:text-gray-400"
    >{prettyBytes(liveSstFilesSize, { binary: true, maximumFractionDigits: 1 })}</td
  >
  <td
    class="hidden px-3 py-2 text-right text-sm whitespace-nowrap text-gray-500 lg:table-cell dark:text-gray-400"
    >{prettyBytes(sizeAllMemTables, { binary: true, maximumFractionDigits: 1 })}</td
  >
</tr>
