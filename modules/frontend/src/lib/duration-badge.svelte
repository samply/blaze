<script lang="ts">
  import Badge from '$lib/tailwind/badge.svelte';

  interface Props {
    duration: number;
  }

  function printDuration(duration: number) {
    return duration < 1000
      ? duration + ' ms'
      : (duration / 1000).toLocaleString(undefined, {
          minimumFractionDigits: 1,
          maximumFractionDigits: 1
        }) + ' s';
  }

  let { duration }: Props = $props();
</script>

<Badge
  color="green"
  title="Request completed in {printDuration(duration)}."
  value="Time: {printDuration(duration)}"
/>
