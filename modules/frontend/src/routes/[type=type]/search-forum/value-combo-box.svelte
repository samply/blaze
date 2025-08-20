<script lang="ts">
  import { ChevronDown } from 'svelte-heros-v2';

  interface Props {
    options?: string[];
    index: number;
    selected: string;
  }

  let { options = [], index, selected = $bindable() }: Props = $props();
</script>

<div class="grow">
  <label for="search-param-{index}-value" class="hidden text-sm font-medium leading-6 text-gray-900"
    >Search Value</label
  >
  <div class="grid grid-cols-1">
    <select
      id="search-param-{index}-value"
      name="value"
      class="col-start-1 row-start-1 w-full appearance-none rounded-md bg-white py-1.5 pr-8 pl-3 text-base text-gray-900 outline-1 -outline-offset-1 outline-gray-300 focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-600 sm:text-sm/6"
      bind:value={selected}
    >
      {#each options as option}
        <option value={option}>{option}</option>
      {/each}
    </select>
    <ChevronDown
      variation="mini"
      class="pointer-events-none col-start-1 row-start-1 mr-2 size-5 self-center justify-self-end text-gray-500 sm:size-4"
    />
  </div>
</div>
