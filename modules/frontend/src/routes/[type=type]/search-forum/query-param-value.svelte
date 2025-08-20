<script lang="ts">
  interface Props {
    index: number;
    value: string;
  }

  let { index, value = $bindable() }: Props = $props();
</script>

<div class="grow">
  <label
    for="search-param-{index}-value"
    class="hidden text-sm font-medium leading-6 text-gray-900 dark:text-gray-100"
    >Search Value</label
  >
  <div class="">
    <input
      type="text"
      id="search-param-{index}-value"
      name="value"
      bind:value
      class="block w-full rounded-md bg-white dark:bg-gray-800 px-3 py-1.5 text-base text-gray-900 dark:text-gray-100 outline-1 -outline-offset-1 outline-gray-300 dark:outline-gray-500 placeholder:text-gray-400 focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-600 focus:dark:outline-indigo-300 sm:text-sm/6"
    />
  </div>
</div>
