<script lang="ts">
  import type { Quantity } from 'fhir/r5';

  interface Props {
    value: Quantity;
  }

  let { value }: Props = $props();
</script>

<p>
  {value.value}
  {#if value.unit}{value.unit}{:else}{value.code}{/if}
</p>
