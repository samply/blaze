<script lang="ts">
  import { createEventDispatcher } from 'svelte';
  import { Plus } from 'svelte-heros-v2';

  const dispatch = createEventDispatcher();

  function click() {
    dispatch('click');
  }
</script>

<button
  type="button"
  class="rounded-md bg-white p-2 text-sm font-semibold text-gray-900 ring-1 ring-gray-300 ring-inset enabled:cursor-pointer enabled:hover:bg-gray-50 disabled:opacity-50 dark:bg-gray-800 dark:text-gray-100 dark:ring-gray-500 enabled:hover:dark:bg-gray-700"
  aria-label="add new search param below"
  onclick={click}
>
  <Plus variation="mini" class="size-5" />
</button>
