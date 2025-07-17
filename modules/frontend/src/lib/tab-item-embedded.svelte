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
    class="border-indigo-500 text-indigo-600 whitespace-nowrap border-b-2 py-4 px-1 text-sm font-medium enabled:cursor-pointer"
    aria-current="page">{label}</button
  >
{:else}
  <button
    onclick={() => {
      selected = name;
    }}
    class="border-transparent text-gray-500 hover:border-gray-300 hover:text-gray-700 whitespace-nowrap border-b-2 py-4 px-1 text-sm font-medium enabled:cursor-pointer"
    >{label}</button
  >
{/if}
