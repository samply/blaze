<script lang="ts">
  import { resolve } from '$app/paths';
  import { grandTotal, rankedByTotal, type ResourceTypeTotal } from './resource-type-totals.js';

  interface Props {
    totals: ResourceTypeTotal[];
  }

  let { totals }: Props = $props();

  let ranked = $derived(rankedByTotal(totals));
  let max = $derived(ranked[0]?.total ?? 0);
  let sum = $derived(grandTotal(totals));

  const numberFormat = new Intl.NumberFormat('en');
  const percentFormat = new Intl.NumberFormat('en', {
    style: 'percent',
    maximumFractionDigits: 1
  });

  function share(total: number): string {
    return sum === 0 ? '' : percentFormat.format(total / sum);
  }

  /** The width of the bar of `total`, at least visible for non-zero totals. */
  function barWidth(total: number): string {
    return max === 0 || total === 0 ? '0' : `max(2px, ${(total / max) * 100}%)`;
  }
</script>

<table class="min-w-full divide-y divide-gray-300 dark:divide-gray-500">
  <thead>
    <tr>
      <th
        scope="col"
        class="py-3.5 pr-3 text-left text-sm font-semibold text-gray-900 dark:text-gray-100"
        >Name</th
      >
      <th scope="col" class="w-full px-3 py-3.5"><span class="sr-only">Distribution</span></th>
      <th
        scope="col"
        class="px-3 py-3.5 text-right text-sm font-semibold whitespace-nowrap text-gray-900 dark:text-gray-100"
        >Total Count</th
      >
      <th
        scope="col"
        class="py-3.5 pl-3 text-right text-sm font-semibold text-gray-900 dark:text-gray-100"
        >Share</th
      >
    </tr>
  </thead>
  <tbody class="divide-y divide-gray-200 dark:divide-gray-600">
    {#each ranked as { name, total } (name)}
      <tr class="hover:bg-gray-50 dark:hover:bg-gray-700/50">
        <td
          class="py-2.5 pr-3 text-sm font-medium whitespace-nowrap text-gray-900 dark:text-gray-100"
          ><a href={resolve('/[type=type]?_summary=true', { type: name })}>{name}</a></td
        >
        <td class="px-3 py-2.5" aria-hidden="true">
          <div
            class="h-3 rounded-r bg-indigo-600 dark:bg-indigo-400"
            style:width={barWidth(total)}
          ></div>
        </td>
        <td
          class="px-3 py-2.5 text-right text-sm whitespace-nowrap text-gray-900 tabular-nums dark:text-gray-100"
          >{numberFormat.format(total)}</td
        >
        <td
          class="py-2.5 pl-3 text-right text-sm whitespace-nowrap text-gray-500 tabular-nums dark:text-gray-400"
          >{share(total)}</td
        >
      </tr>
    {/each}
  </tbody>
</table>
