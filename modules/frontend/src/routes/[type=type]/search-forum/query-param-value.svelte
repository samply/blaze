<script lang="ts">
  interface Props {
    index: number;
    value: string;
  }

  let { index, value = $bindable() }: Props = $props();
</script>

<div class="flex-grow">
  <label for="search-param-{index}-value" class="hidden text-sm font-medium leading-6 text-gray-900"
    >Search Value</label
  >
  <div class="">
    <input
      type="text"
      id="search-param-{index}-value"
      name="value"
      bind:value
      class="block w-full rounded-md border-0 py-1.5 text-gray-900 ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
    />
  </div>
</div>
