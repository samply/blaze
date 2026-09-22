<script lang="ts">
  import type { FhirPrimitive } from '$lib/resource/resource-card.js';
  import { targetType } from './canonical.js';
  import { resolve } from '$app/paths';

  interface Props {
    value: FhirPrimitive;
  }

  let { value }: Props = $props();

  let type = $derived(targetType(value.type));
</script>

{#if type && typeof value.value === 'string'}
  <a
    href="{resolve('/[type=type]', { type: type })}?_summary=true&url={encodeURIComponent(
      value.value
    )}"
    class="font-medium text-indigo-600 hover:text-indigo-500 dark:text-indigo-300 hover:dark:text-indigo-400"
    >{value.value}</a
  >
{:else}
  {value.value}
{/if}
