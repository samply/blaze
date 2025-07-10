<script lang="ts">
  import type { CodeableConcept } from 'fhir/r4';
  import Coding from './coding.svelte';

  interface Props {
    value: CodeableConcept;
  }

  let { value }: Props = $props();
</script>

{#if value.coding}
  {#each value.coding as coding}
    <Coding value={coding} />
  {/each}
{:else if value.text !== undefined}
  <p>{value.text}</p>
{/if}
