<script lang="ts">
  import type { AgeClassCounts, Demographics } from './demographics.js';

  interface Props {
    demographics: Demographics;
  }

  let { demographics }: Props = $props();

  type Gender = 'male' | 'female';

  interface Hovered {
    ageClass: number;
    gender: Gender;
  }

  const genderLabels: Record<Gender, string> = { male: 'Male', female: 'Female' };

  const numberFormat = new Intl.NumberFormat('en');
  const percentFormat = new Intl.NumberFormat('en', {
    style: 'percent',
    maximumFractionDigits: 1
  });

  const niceFractions = [1, 1.5, 2, 3, 4, 5, 6, 8, 10];

  /** rounds up to a nice fraction of a power of ten, whose half is nice as well */
  function niceCeil(value: number): number {
    if (value <= 0) return 2;
    const power = Math.pow(10, Math.floor(Math.log10(value)));
    const fraction = niceFractions.find((f) => value / power <= f) ?? 10;
    return Math.max(2, fraction * power);
  }

  // the oldest age class on top
  let rows = $derived([...demographics.ageClasses].reverse());
  let max = $derived(niceCeil(Math.max(...rows.map((row) => Math.max(row.male, row.female)))));
  let ticks = $derived([max, max / 2, 0, max / 2, max]);

  let hovered = $state<Hovered | undefined>();

  function barWidth(count: number): string {
    return count === 0 ? '0' : `max(2px, ${(count / max) * 100}%)`;
  }

  function isHovered(row: AgeClassCounts, gender: Gender): boolean {
    return hovered?.ageClass === row.ageClass && hovered.gender === gender;
  }

  function tooltip(row: AgeClassCounts, gender: Gender): string {
    const count = row[gender];
    const share =
      demographics.total === 0 ? '' : ` (${percentFormat.format(count / demographics.total)})`;
    return `${numberFormat.format(count)}${share}`;
  }
</script>

{#snippet half(row: AgeClassCounts, gender: Gender)}
  <div
    role="presentation"
    class="relative flex h-6 items-center {gender === 'male' ? 'justify-end' : ''}"
    onmouseenter={() => (hovered = { ageClass: row.ageClass, gender })}
    onmouseleave={() => (hovered = undefined)}
  >
    <div
      class="h-4 {gender === 'male'
        ? 'rounded-l bg-viz-1 dark:bg-viz-1-dark'
        : 'rounded-r bg-viz-2 dark:bg-viz-2-dark'} {hovered && !isHovered(row, gender)
        ? 'opacity-60'
        : ''}"
      style:width={barWidth(row[gender])}
    ></div>
    {#if isHovered(row, gender)}
      <div
        class="pointer-events-none absolute bottom-full z-10 mb-1 rounded-md bg-white px-2.5 py-1.5 text-xs whitespace-nowrap shadow-lg ring-1 ring-gray-900/10 dark:bg-gray-700 dark:ring-white/10 {gender ===
        'male'
          ? 'right-0'
          : 'left-0'}"
      >
        <div class="text-gray-500 dark:text-gray-400">
          {genderLabels[gender]}, {row.label} years
        </div>
        <div class="font-medium text-gray-900 tabular-nums dark:text-gray-100">
          {tooltip(row, gender)}
        </div>
      </div>
    {/if}
  </div>
{/snippet}

<div aria-hidden="true">
  <div class="grid grid-cols-[1fr_4rem_1fr] text-sm font-medium text-gray-900 dark:text-gray-100">
    <div class="flex items-center justify-end gap-1.5">
      <span class="size-2.5 rounded-sm bg-viz-1 dark:bg-viz-1-dark"></span>
      {genderLabels.male}
    </div>
    <div class="text-center text-xs font-normal text-gray-500 dark:text-gray-400">Age</div>
    <div class="flex items-center gap-1.5">
      <span class="size-2.5 rounded-sm bg-viz-2 dark:bg-viz-2-dark"></span>
      {genderLabels.female}
    </div>
  </div>
  <div class="mt-2 grid grid-cols-[1fr_4rem_1fr] gap-y-0.5">
    {#each rows as row (row.ageClass)}
      {@render half(row, 'male')}
      <div
        class="flex items-center justify-center text-xs text-gray-500 tabular-nums dark:text-gray-400"
      >
        {row.label}
      </div>
      {@render half(row, 'female')}
    {/each}
  </div>
  <div
    class="mt-1 grid grid-cols-[1fr_4rem_1fr] border-t border-gray-300 pt-1 text-[11px] text-gray-500 tabular-nums dark:border-gray-500 dark:text-gray-400"
  >
    <div class="flex justify-between">
      <span>{numberFormat.format(ticks[0])}</span>
      <span>{numberFormat.format(ticks[1])}</span>
      <span></span>
    </div>
    <div class="text-center">{ticks[2]}</div>
    <div class="flex justify-between">
      <span></span>
      <span>{numberFormat.format(ticks[3])}</span>
      <span>{numberFormat.format(ticks[4])}</span>
    </div>
  </div>
</div>

<table class="sr-only">
  <caption>Patients by age class and gender</caption>
  <thead>
    <tr><th scope="col">Age</th><th scope="col">Male</th><th scope="col">Female</th></tr>
  </thead>
  <tbody>
    {#each demographics.ageClasses as row (row.ageClass)}
      <tr><th scope="row">{row.label}</th><td>{row.male}</td><td>{row.female}</td></tr>
    {/each}
  </tbody>
</table>
