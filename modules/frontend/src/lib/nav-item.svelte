<script lang="ts">
  import type { Pathname } from '$app/types';
  import { resolve } from '$app/paths';

  interface Props {
    active?: boolean;
    id: Pathname;
    label: string;
  }

  let { active = false, id, label }: Props = $props();
</script>

{#if active}
  <a
    href={resolve(id)}
    class="inline-flex items-center border-b-2 border-indigo-500 px-1 pt-1 text-sm font-medium text-gray-900 dark:text-gray-100"
    aria-current="page">{label}</a
  >
{:else}
  <a
    href={resolve(id)}
    class="inline-flex items-center border-b-2 border-transparent px-1 pt-1 text-sm font-medium text-gray-500 hover:border-gray-300 hover:text-gray-700 dark:text-gray-400 hover:dark:border-gray-500 hover:dark:text-gray-300"
    >{label}</a
  >
{/if}
