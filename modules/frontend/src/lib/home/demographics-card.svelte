<script lang="ts">
  import { demographicsCql, type Demographics } from './demographics.js';
  import PopulationPyramid from './population-pyramid.svelte';

  interface Props {
    demographics: Demographics;
  }

  let { demographics }: Props = $props();

  const numberFormat = new Intl.NumberFormat('en');

  function patients(count: number): string {
    return `${numberFormat.format(count)} ${count === 1 ? 'patient' : 'patients'}`;
  }

  let otherGenders = $derived(
    Object.entries(demographics.genderTotals)
      .filter(([gender]) => gender !== 'male' && gender !== 'female')
      .map(([gender, count]) => `${gender} (${numberFormat.format(count)})`)
      .join(', ')
  );
</script>

<PopulationPyramid {demographics} />

<div class="mt-4 space-y-1 text-xs text-gray-500 dark:text-gray-400">
  {#if demographics.notShown > 0}
    <p>
      {patients(demographics.notShown)} not shown{#if otherGenders}, with gender {otherGenders}{/if}{#if demographics.unknownAge > 0}{otherGenders
          ? ' or'
          : ','} with unknown age ({numberFormat.format(demographics.unknownAge)}){/if}.
    </p>
  {/if}
  <p>
    Calculated over {patients(demographics.total)} by <code>$evaluate-measure</code>
    {#if demographics.evalDurationSeconds !== undefined}
      in {demographics.evalDurationSeconds.toLocaleString('en', { maximumFractionDigits: 2 })} s
    {/if}
    using an inline Measure.
  </p>
</div>

<details class="mt-3 text-sm">
  <summary class="cursor-pointer text-indigo-600 hover:text-indigo-500 dark:text-indigo-400"
    >Show CQL</summary
  >
  <pre
    class="mt-2 overflow-x-auto rounded-md bg-gray-50 p-3 text-xs text-gray-800 dark:bg-gray-900 dark:text-gray-200">{demographicsCql}</pre>
</details>
