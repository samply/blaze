<script lang="ts">
  import { ChevronDown } from 'svelte-heros-v2';

  interface Props {
    searchParams: { name: string }[];
    index: number;
    selected: string;
  }

  let { searchParams, index, selected = $bindable() }: Props = $props();
</script>

<div>
  <label
    for="search-param-{index}-search-param"
    class="hidden text-sm leading-6 font-medium text-gray-900 dark:text-gray-100"
    >Search Param</label
  >
  <div class="grid grid-cols-1">
    <select
      id="search-param-{index}-search-param"
      name="search-param"
      class="col-start-1 row-start-1 ml-1 w-full appearance-none rounded-md bg-white py-1.5 pr-2.5 pl-3 text-base text-gray-900 outline-1 -outline-offset-1 outline-gray-300 focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-600 sm:text-sm/6 dark:bg-gray-800 dark:text-gray-100 dark:outline-gray-500 focus:dark:outline-indigo-300"
      bind:value={selected}
    >
      <option value="__select">please select</option>
      {#each searchParams as searchParam}
        <option value={searchParam.name}>{searchParam.name}</option>
      {/each}
    </select>
    <ChevronDown
      variation="mini"
      class="pointer-events-none col-start-1 row-start-1 mr-2 size-5 self-center justify-self-end text-gray-500 sm:size-4 dark:text-gray-400"
    />
  </div>
</div>
