<script lang="ts">
  import { createEventDispatcher } from 'svelte';

  interface Props {
    index: number;
    active: boolean;
  }

  let { index, active }: Props = $props();

  const dispatch = createEventDispatcher();

  function change() {
    dispatch('change');
  }
</script>

<div class="flex h-6 shrink-0 items-center">
  <label
    for="search-param-{index}-active"
    class="hidden text-sm font-medium leading-6 text-gray-900 dark:text-gray-100">Active</label
  >
  <div class="group grid size-4 grid-cols-1 m-2">
    <input
      id="search-param-{index}-active"
      name="active"
      type="checkbox"
      checked={active}
      class="h-5 w-5 my-1 col-start-1 row-start-1 appearance-none rounded-sm border border-gray-300 bg-white dark:bg-gray-800 checked:border-indigo-600 checked:bg-indigo-600 indeterminate:border-indigo-600 indeterminate:bg-indigo-600 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600 disabled:border-gray-300 disabled:bg-gray-100 disabled:checked:bg-gray-100 forced-colors:appearance-auto"
      onchange={change}
    />
    <svg
      viewBox="-1 0 14 14"
      fill="none"
      class="pointer-events-none col-start-1 row-start-1 size-3.5 self-center justify-self-center stroke-white group-has-disabled:stroke-gray-950/25"
    >
      <path
        d="M3 8L6 12L12 3.5"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
        class="opacity-0 group-has-checked:opacity-100"
      />
      <path
        d="M3 7H11"
        stroke-width="2"
        stroke-linecap="round"
        stroke-linejoin="round"
        class="opacity-0 group-has-indeterminate:opacity-100"
      />
    </svg>
  </div>
</div>
