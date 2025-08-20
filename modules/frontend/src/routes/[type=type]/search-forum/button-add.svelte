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
  class="rounded-md bg-white dark:bg-gray-800 p-2 text-sm font-semibold text-gray-900 dark:text-gray-100 disabled:opacity-50 ring-1 ring-inset ring-gray-300 dark:ring-gray-500 enabled:hover:bg-gray-50 enabled:hover:dark:bg-gray-700 enabled:cursor-pointer"
  aria-label="add new search param below"
  onclick={click}
>
  <Plus variation="mini" class="size-5" />
</button>
