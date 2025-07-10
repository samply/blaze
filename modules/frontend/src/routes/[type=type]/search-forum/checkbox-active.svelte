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

<div>
  <label
    for="search-param-{index}-active"
    class="hidden text-sm font-medium leading-6 text-gray-900">Active</label
  >
  <input
    id="search-param-{index}-active"
    name="active"
    type="checkbox"
    checked={active}
    class="h-5 w-5 m-2 rounded border-gray-300 text-indigo-600 focus:ring-indigo-600"
    onchange={change}
  />
</div>
