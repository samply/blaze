<script lang="ts">
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
    class="hidden text-sm font-medium leading-6 text-gray-900">Search Param</label
  >
  <select
    id="search-param-{index}-search-param"
    name="search-param"
    class="block w-full rounded-md border-0 py-1.5 pl-3 pr-10 text-gray-900 ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-indigo-600 sm:text-sm sm:leading-6"
    bind:value={selected}
  >
    <option value="__select">please select</option>
    {#each searchParams as searchParam}
      <option value={searchParam.name}>{searchParam.name}</option>
    {/each}
  </select>
</div>
