<script lang="ts">
  interface Props {
    options?: string[];
    index: number;
    selected: string;
  }

  let { options = [], index, selected = $bindable() }: Props = $props();
</script>

<div class="flex-grow">
  <label for="search-param-{index}-value" class="hidden text-sm font-medium leading-6 text-gray-900"
    >Search Value</label
  >
  <select
    id="search-param-{index}-value"
    name="value"
    class="block w-full rounded-md border-0 py-1.5 pl-3 pr-10 text-gray-900 ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-indigo-600 sm:text-sm sm:leading-6"
    bind:value={selected}
  >
    {#each options as option}
      <option value={option}>{option}</option>
    {/each}
  </select>
</div>
