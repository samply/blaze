<script lang="ts">
  interface Props {
    name: string;
    label: string;
    selected: string;
  }

  let { name, label, selected = $bindable() }: Props = $props();
</script>

{#if name === selected}
  <button
    onclick={() => {
      selected = name;
    }}
    class="border-indigo-500 text-indigo-600 dark:text-indigo-300 whitespace-nowrap border-b-2 py-4 px-1 text-sm font-medium enabled:cursor-pointer"
    aria-current="page">{label}</button
  >
{:else}
  <button
    onclick={() => {
      selected = name;
    }}
    class="border-transparent text-gray-500 dark:text-gray-400 hover:border-gray-300 hover:dark:border-gray-500 hover:text-gray-700 hover:dark:text-gray-300 whitespace-nowrap border-b-2 py-4 px-1 text-sm font-medium enabled:cursor-pointer"
    >{label}</button
  >
{/if}
