<script lang="ts">
  import type { Address } from 'fhir/r4';
  import { joinStrings } from '$lib/util';

  interface Props {
    value: Address;
  }

  let { value }: Props = $props();
</script>

{#each value.line ?? [] as line}
  <p>{line}</p>
{/each}
{#if value.postalCode || value.city}
  <p>{joinStrings(' ', value.postalCode, value.city)}</p>
{/if}
{#if value.state || value.country}
  <p>{joinStrings(' ', value.state, value.country)}</p>
{/if}
