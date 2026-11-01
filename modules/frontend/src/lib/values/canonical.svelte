<script lang="ts">
  import type { ElementType } from '#lib/element-types.js';
  import { targetType } from './canonical.js';
  import { resolve } from '$app/paths';

  interface Props {
    type: ElementType;
    value: string;
  }

  let { type, value }: Props = $props();

  let target = $derived(targetType(type));
</script>

{#if target}
  <a
    href="{resolve('/[type=type]', { type: target })}?_summary=true&url={encodeURIComponent(value)}"
    class="font-medium text-indigo-600 hover:text-indigo-500 dark:text-indigo-300 hover:dark:text-indigo-400"
    >{value}</a
  >
{:else}
  {value}
{/if}
