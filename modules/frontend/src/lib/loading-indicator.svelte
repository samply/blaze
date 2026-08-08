<script lang="ts">
  import { fade, slide } from 'svelte/transition';

  interface Props {
    start: number;
  }

  let { start }: Props = $props();

  const delayMillis = 300;

  let now = $state(Date.now());

  $effect(() => {
    const interval = setInterval(() => (now = Date.now()), 100);
    return () => clearInterval(interval);
  });

  /**
   * Deriving the elapsed time from the clock and the current `start` — instead
   * of accumulating it into state — makes it drop back to zero the moment a new
   * query starts. The clock lags up to one tick behind, so a `start` taken after
   * the last tick is clamped to zero.
   */
  let elapsed = $derived(Math.max(0, now - start));
</script>

{#if elapsed > delayMillis}
  <div
    in:fade|global={{ duration: 200 }}
    out:slide|global={{ duration: 200 }}
    class="px-4 py-5 text-center text-gray-700 sm:px-6 dark:text-gray-300"
  >
    <code>
      loading...
      {(elapsed / 1000).toLocaleString(undefined, {
        minimumFractionDigits: 1,
        maximumFractionDigits: 1
      })}
      s
    </code>
  </div>
{/if}
