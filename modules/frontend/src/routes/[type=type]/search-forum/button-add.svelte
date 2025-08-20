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
  class="rounded-md bg-white p-2 text-sm font-semibold text-gray-900 disabled:opacity-50 ring-1 ring-inset ring-gray-300 enabled:hover:bg-gray-50 enabled:cursor-pointer"
  aria-label="add new search param below"
  onclick={click}
>
  <Plus variation="mini" class="size-5" />
</button>
