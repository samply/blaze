<script lang="ts">
  import { resolve } from '$app/paths';

  interface Props {
    active?: boolean;
    id: '/__admin' | '/__admin/dbs' | '/__admin/jobs' | '/__admin/cql';
    label: string;
  }

  let { active = false, id, label }: Props = $props();
</script>

{#if active}
  <a
    href={resolve(id)}
    class="border-b-2 border-indigo-500 px-1 py-4 text-sm font-medium whitespace-nowrap text-indigo-600 dark:text-indigo-300"
    aria-current="page">{label}</a
  >
{:else}
  <a
    href={resolve(id)}
    class="border-b-2 border-transparent px-1 py-4 text-sm font-medium whitespace-nowrap text-gray-500 hover:border-gray-300 hover:text-gray-700 dark:text-gray-400 hover:dark:border-gray-500 hover:dark:text-gray-300"
    >{label}</a
  >
{/if}
