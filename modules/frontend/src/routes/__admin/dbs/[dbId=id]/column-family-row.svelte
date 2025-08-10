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
  <td class="whitespace-nowrap py-2 pl-4 pr-3 text-sm font-medium text-gray-900 sm:pl-0">
    <a
      href={resolve('/__admin/dbs/[dbId=id]/column-families/[cfId=id]', { dbId: dbId, cfId: name })}
      >{pascalCase(name)}</a
    >
  </td>
  <td class="hidden whitespace-nowrap px-3 py-2 text-right text-sm text-gray-500 sm:table-cell"
    >{prettyNum(estimateNumKeys, { maximumFractionDigits: 1 })}</td
  >
  <td class="whitespace-nowrap px-3 py-2 text-right text-sm text-gray-500"
    >{prettyBytes(liveSstFilesSize, { binary: true, maximumFractionDigits: 1 })}</td
  >
  <td class="hidden whitespace-nowrap px-3 py-2 text-right text-sm text-gray-500 lg:table-cell"
    >{prettyBytes(sizeAllMemTables, { binary: true, maximumFractionDigits: 1 })}</td
  >
</tr>
