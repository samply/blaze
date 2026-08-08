<script lang="ts">
  interface Props {
    /** overall score between 0 and 100 */
    score: number;
    rating?: string;
  }

  let { score, rating }: Props = $props();

  interface Band {
    label: string;
    /** inclusive lower bound of the band */
    min: number;
    /** exclusive upper bound of the band (inclusive for the top band) */
    max: number;
    /** label text color */
    text: string;
  }

  // the rating bands, matching the score thresholds: < 25 insufficient,
  // ≥ 25 acceptable, ≥ 50 good, ≥ 80 excellent — ordered high to low so they
  // stack top-down like a legend
  const bands: Band[] = [
    { label: 'excellent', min: 80, max: 100, text: 'text-blue-600 dark:text-blue-400' },
    { label: 'good', min: 50, max: 80, text: 'text-green-600 dark:text-green-400' },
    { label: 'acceptable', min: 25, max: 50, text: 'text-orange-600 dark:text-orange-400' },
    { label: 'insufficient', min: 0, max: 25, text: 'text-red-600 dark:text-red-400' }
  ];

  let clamped = $derived(Math.max(0, Math.min(100, score)));
  let current = $derived(
    bands.find((band) => band.label === rating) ??
      bands.find((band) => clamped >= band.min && (clamped < band.max || band.max === 100)) ??
      bands[bands.length - 1]
  );
</script>

<div
  class="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-2 bg-white px-4 py-10 sm:px-6 xl:px-8 dark:bg-gray-800"
>
  <dt class="text-sm leading-6 font-medium text-gray-500 dark:text-gray-400">Score</dt>
  <dd class="flex w-full flex-none items-center justify-between gap-x-4">
    <span class="text-3xl leading-10 font-medium tracking-tight tabular-nums">
      <span class="text-gray-900 dark:text-gray-100">{clamped.toFixed(1)}</span>
      <span class="text-lg text-gray-500 dark:text-gray-400">/ 100</span>
    </span>
    <ol class="text-right leading-tight">
      {#each bands as band (band.label)}
        <li
          class={band.label === current.label
            ? `text-sm font-bold ${band.text}`
            : 'text-[11px] text-gray-400 dark:text-gray-500'}
        >
          {band.label}
        </li>
      {/each}
    </ol>
  </dd>
</div>
