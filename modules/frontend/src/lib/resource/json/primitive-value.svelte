<script lang="ts">
  interface Props {
    value: unknown;
  }

  let { value }: Props = $props();
</script>

{#if typeof value === 'number'}<span class="text-green-600 dark:text-green-400">{value}</span
  >{:else if typeof value === 'boolean' || value === null}<span
    class="text-blue-800 dark:text-blue-300">{String(value)}</span
  >{:else}<span class="text-blue-600 dark:text-blue-200">{JSON.stringify(value)}</span>{/if}
