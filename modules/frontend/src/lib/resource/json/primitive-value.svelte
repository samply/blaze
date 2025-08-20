<script lang="ts">
  import type { FhirPrimitive } from '../resource-card.js';

  interface Props {
    value: FhirPrimitive;
  }

  let { value }: Props = $props();
</script>

{#if ['integer', 'decimal', 'unsingedInt', 'positiveInt'].includes(value.type.code)}<span
    class="text-green-600 dark:text-green-400">{value.value}</span
  >{:else if value.type.code === 'boolean'}<span class="text-blue-800">{value.value}</span
  >{:else}<span class="text-blue-600 dark:text-blue-200">"{value.value}"</span>{/if}
