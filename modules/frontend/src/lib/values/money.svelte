<script lang="ts">
  import type { Money } from 'fhir/r4';

  interface Props {
    value: Money;
  }

  let { value }: Props = $props();
</script>

{#if value.value}
  {value.value}
  {#if value.currency}{value.currency}{/if}
{:else}
  unknown
{/if}
