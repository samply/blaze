<script lang="ts">
  import type { Period } from 'fhir/r4';
  import DateTime from './date-time.svelte';
  import UseBadge from './util/gray-badge.svelte';

  interface Props {
    value: Period;
  }

  let { value }: Props = $props();
</script>

{#if value.start && value.end}
  <p>
    <DateTime value={value.start} />
  </p>

  <p class="pt-2">
    <DateTime value={value.end} />
  </p>
{:else if value.start}
  <p>
    <DateTime value={value.start} />
    <UseBadge value="start" />
  </p>
{:else if value.end}
  <p>
    <DateTime value={value.end} />
    <UseBadge value="end" />
  </p>
{/if}
