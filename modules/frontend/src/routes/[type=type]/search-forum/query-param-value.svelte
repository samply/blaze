<script lang="ts">
  interface Props {
    index: number;
    value: string;
  }

  let { index, value = $bindable() }: Props = $props();
</script>

<div class="grow">
  <label for="search-param-{index}-value" class="hidden text-sm font-medium leading-6 text-gray-900"
    >Search Value</label
  >
  <div class="">
    <input
      type="text"
      id="search-param-{index}-value"
      name="value"
      bind:value
      class="block w-full rounded-md bg-white px-3 py-1.5 text-base text-gray-900 outline-1 -outline-offset-1 outline-gray-300 placeholder:text-gray-400 focus:outline-2 focus:-outline-offset-2 focus:outline-indigo-600 sm:text-sm/6"
    />
  </div>
</div>
