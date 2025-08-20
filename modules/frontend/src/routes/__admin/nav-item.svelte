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
    class="border-indigo-500 text-indigo-600 dark:text-indigo-300 whitespace-nowrap border-b-2 py-4 px-1 text-sm font-medium"
    aria-current="page">{label}</a
  >
{:else}
  <a
    href={resolve(id)}
    class="border-transparent text-gray-500 dark:text-gray-400 hover:border-gray-300 hover:dark:border-gray-500 hover:text-gray-700 hover:dark:text-gray-300 whitespace-nowrap border-b-2 py-4 px-1 text-sm font-medium"
    >{label}</a
  >
{/if}
