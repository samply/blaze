<script lang="ts">
  import type { ResolvedPathname } from '$app/types';

  interface Props {
    active?: boolean;
    id: ResolvedPathname;
    label: string;
  }

  let { active = false, id, label }: Props = $props();
</script>

<!-- eslint-disable svelte/no-navigation-without-resolve -->

{#if active}
  <a
    href={id}
    class="border-indigo-500 text-gray-900 dark:text-gray-100 inline-flex items-center border-b-2 px-1 pt-1 text-sm font-medium"
    aria-current="page">{label}</a
  >
{:else}
  <a
    href={id}
    class="border-transparent text-gray-500 dark:text-gray-400 hover:border-gray-300 hover:dark:border-gray-500 hover:text-gray-700 hover:dark:text-gray-300 inline-flex items-center border-b-2 px-1 pt-1 text-sm font-medium"
    >{label}</a
  >
{/if}
