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
    class="border-b-2 border-indigo-500 px-1 py-4 text-sm font-medium whitespace-nowrap text-indigo-600 enabled:cursor-pointer dark:text-indigo-300"
    aria-current="page">{label}</button
  >
{:else}
  <button
    onclick={() => {
      selected = name;
    }}
    class="border-b-2 border-transparent px-1 py-4 text-sm font-medium whitespace-nowrap text-gray-500 hover:border-gray-300 hover:text-gray-700 enabled:cursor-pointer dark:text-gray-400 hover:dark:border-gray-500 hover:dark:text-gray-300"
    >{label}</button
  >
{/if}
